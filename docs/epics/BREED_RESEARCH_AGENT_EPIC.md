# Epic: Breed Research Agent

## Overview

Build an autonomous AI agent that researches chicken breeds in depth, verifies and enriches breed information, and maintains source citations. The agent will prioritize breeds that haven't been updated recently (or never updated) and produce comprehensive research reports before updating breed entries.

## Background & Context

### Existing Architecture Reference
- **Chicken Facts Agent**: `src/main/kotlin/co/qwex/chickenapi/ai/KoogChickenFactsAgent.kt`
- **Research Strategy**: `src/main/kotlin/co/qwex/chickenapi/ai/ChickenResearchStrategy.kt`
- **Koog Framework**: `ai.koog:koog-agents:0.5.2` - provides agent lifecycle, tool registry, graph-based strategies
- **Current Breed Model**: `src/main/kotlin/co/qwex/chickenapi/model/Breed.kt`

### Key Differences from Chicken Facts Agent
| Aspect | Chicken Facts Agent | Breed Research Agent |
|--------|---------------------|---------------------|
| **Input** | None (general search) | Specific breed to research |
| **Output** | Single fact + 1 source | Full report + multiple sources |
| **Data Mutation** | Appends new record | Updates existing breed record |
| **Selection Logic** | Random topic | Oldest `updatedAt` / null priority |
| **Sources** | Single URL | Multiple verified sources |

---

## Data Model Changes

### 1. Add Sources to Breed Model

**File**: `src/main/kotlin/co/qwex/chickenapi/model/Breed.kt`

```kotlin
data class Breed(
    val id: Int,
    val name: String,
    val origin: String?,
    val eggColor: String?,
    val eggSize: String?,
    val temperament: String?,
    val description: String?,
    val imageUrl: String?,
    val numEggs: Int?,
    val updatedAt: Instant? = null,
    val sources: List<String>? = null,  // NEW: List of source URLs
)
```

### 2. Create BreedResearchRecord Model

**File**: `src/main/kotlin/co/qwex/chickenapi/model/BreedResearchRecord.kt`

```kotlin
data class BreedResearchRecord(
    val runId: String,                    // UUID for this agent run
    val breedId: Int,                     // Which breed was researched
    val breedName: String,                // Breed name at time of research
    val startedAt: Instant,               // When the run started
    val completedAt: Instant,             // When the run completed
    val durationMillis: Long,             // Total execution time
    val outcome: AgentRunOutcome,         // SUCCESS | NO_OUTPUT | FAILED
    val report: String?,                  // Full research report
    val sourcesFound: List<String>,       // All sources discovered
    val fieldsUpdated: List<String>,      // Which breed fields were updated
    val errorMessage: String? = null,     // Error details if failed
)
```

### 3. Extend Google Sheets Schema

**breeds sheet**: Add column K for `sources` (comma-separated URLs or JSON array)

**New sheet**: `breed_research_runs` for logging research history
- Columns: runId, breedId, breedName, startedAt, completedAt, durationMillis, outcome, reportLength, sourcesCount, fieldsUpdated, errorMessage

---

## Agent Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                    KoogBreedResearchAgent                           │
│                         (Spring Service)                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────┐    ┌──────────────────────────────────────┐   │
│  │  Configuration  │    │           Tool Registry              │   │
│  │  Properties     │    ├──────────────────────────────────────┤   │
│  └─────────────────┘    │ • GetNextBreedToResearchTool         │   │
│                         │ • GetBreedDetailsTool                 │   │
│                         │ • WebSearchTool (reuse)               │   │
│                         │ • WebFetchTool (reuse)                │   │
│                         │ • SaveBreedResearchTool               │   │
│                         └──────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │              BreedResearchStrategy                          │   │
│  │         (Graph-based Agent Strategy)                        │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│              BreedResearcherScheduledTaskService                    │
│                    (Spring Scheduler)                               │
│  • Runs every 24 hours                                              │
│  • Invokes agent                                                    │
│  • Persists research records                                        │
└─────────────────────────────────────────────────────────────────────┘
```

### Agent Strategy Flow

```
                    ┌──────────────┐
                    │    START     │
                    └──────┬───────┘
                           │
                           ▼
                ┌────────────────────┐
                │ Reset Counters &   │
                │ State Variables    │
                └─────────┬──────────┘
                          │
                          ▼
              ┌──────────────────────┐
              │ Get Next Breed       │
              │ (oldest updatedAt)   │
              └─────────┬────────────┘
                        │
                        ▼
              ┌──────────────────────┐
              │ Get Breed Details    │
              │ (current data)       │
              └─────────┬────────────┘
                        │
                        ▼
            ┌─────────────────────────┐
            │     Research Loop       │◄─────────────┐
            │  (web_search/web_fetch) │              │
            └───────────┬─────────────┘              │
                        │                            │
                   ┌────┴────┐                       │
                   │ Under   │ Yes                   │
                   │ 8 tool  ├───────────────────────┘
                   │ calls?  │
                   └────┬────┘
                        │ No
                        ▼
            ┌─────────────────────────┐
            │  Force Save Research    │
            │  (synthesize report)    │
            └───────────┬─────────────┘
                        │
                        ▼
            ┌─────────────────────────┐
            │  Update Breed Entry     │
            │  + Add Sources          │
            └───────────┬─────────────┘
                        │
                        ▼
                ┌──────────────┐
                │    FINISH    │
                └──────────────┘
```

---

## Tools Specification

### Tool 1: GetNextBreedToResearchTool

**Purpose**: Select the breed that needs research most urgently

**Selection Algorithm**:
1. Fetch all breeds from repository
2. Filter to breeds with `updatedAt = null`
3. If any nulls exist → return random one from nulls
4. Otherwise → return breed with oldest `updatedAt`

```kotlin
class GetNextBreedToResearchTool(
    private val breedRepository: BreedRepository,
) : SimpleTool<GetNextBreedToResearchTool.Args>() {

    @Serializable
    data class Args(
        @property:LLMDescription("Set to true to get the next breed")
        val fetch: Boolean = true,
    )

    @Serializable
    data class Result(
        val breedId: Int,
        val breedName: String,
        val lastUpdated: String?,
        val reason: String,  // "never_updated" | "oldest_update"
    )

    override val name = "get_next_breed_to_research"
    override val description = "Retrieves the next chicken breed that needs research..."
}
```

### Tool 2: GetBreedDetailsTool

**Purpose**: Fetch current breed information to understand what needs verification

```kotlin
class GetBreedDetailsTool(
    private val breedRepository: BreedRepository,
) : SimpleTool<GetBreedDetailsTool.Args>() {

    @Serializable
    data class Args(
        @property:LLMDescription("The ID of the breed to fetch details for")
        val breedId: Int,
    )

    @Serializable
    data class Result(
        val id: Int,
        val name: String,
        val origin: String?,
        val eggColor: String?,
        val eggSize: String?,
        val temperament: String?,
        val description: String?,
        val numEggs: Int?,
        val currentSources: List<String>,
    )

    override val name = "get_breed_details"
    override val description = "Fetches the current information for a specific breed..."
}
```

### Tool 3: WebSearchTool (Reuse)

**Note**: Reuse existing `WebSearchTool` from `KoogChickenFactsAgent.kt:275-365`

Modify system prompt in the breed research strategy to focus searches on:
- Breed-specific characteristics
- Origin and history
- Egg production facts
- Temperament and behavior
- Reputable poultry sources (backyardchickens.com, mypetchicken.com, etc.)

### Tool 4: WebFetchTool (Reuse)

**Note**: Reuse existing `WebFetchTool` from `KoogChickenFactsAgent.kt:371-440`

### Tool 5: SaveBreedResearchTool

**Purpose**: Final tool that captures the complete research output

```kotlin
class SaveBreedResearchTool : SimpleTool<SaveBreedResearchTool.Args>() {

    @Serializable
    data class Args(
        @property:LLMDescription("The breed ID being researched")
        val breedId: Int,

        @property:LLMDescription("Full research report (2-4 paragraphs) about what makes this breed unique")
        val report: String,

        @property:LLMDescription("Updated/verified origin (country or region)")
        val origin: String?,

        @property:LLMDescription("Updated/verified egg color")
        val eggColor: String?,

        @property:LLMDescription("Updated/verified egg size (small, medium, large, extra-large)")
        val eggSize: String?,

        @property:LLMDescription("Updated/verified temperament description")
        val temperament: String?,

        @property:LLMDescription("Updated/enriched description of the breed")
        val description: String?,

        @property:LLMDescription("Updated/verified annual egg production number")
        val numEggs: Int?,

        @property:LLMDescription("List of source URLs that were used to verify information")
        val sources: List<String>,
    )

    override val name = "save_breed_research"
    override val description = """
        Saves the complete research findings for a chicken breed.
        This tool should be called once you have:
        1. Thoroughly researched the breed
        2. Written a comprehensive report about what makes it unique
        3. Verified all existing data points
        4. Collected source URLs for citations

        The report should cover: history, physical characteristics, temperament,
        egg production, and any unique or interesting facts about the breed.
    """.trimIndent()
}
```

---

## Strategy Implementation

### File: `src/main/kotlin/co/qwex/chickenapi/ai/BreedResearchStrategy.kt`

```kotlin
fun breedResearchStrategy(
    maxToolCalls: Int = 8,
): AIAgentGraphStrategy<String, String> = strategy<String, String>("breed_research") {

    var toolCalls = 0
    var currentBreedId: Int? = null
    var savedResearchJson: String? = null

    // Node definitions...
    val resetState by node<String, String>("reset_state") { input ->
        toolCalls = 0
        currentBreedId = null
        savedResearchJson = null
        input
    }

    val callLLM by nodeLLMRequest(name = "call_llm", allowToolCalls = true)
    val executeTool by nodeExecuteTool()
    val captureResult by node<ReceivedToolResult, ReceivedToolResult>("capture_result") { result ->
        // Capture breed ID from get_next_breed
        // Capture research JSON from save_breed_research
        result
    }
    val sendToolResult by nodeLLMSendToolResult()
    val forceResearchSave by nodeLLMRequest(name = "force_save", allowToolCalls = true)
    val returnResult by node<String, String>("return_result") { savedResearchJson ?: "{}" }

    // Graph edges (similar pattern to ChickenResearchStrategy)...
}
```

---

## System Prompt

```kotlin
val breedResearchPrompt = """
You are a chicken breed research specialist with deep knowledge of poultry breeds from around the world.

Your task is to thoroughly research a specific chicken breed and produce a comprehensive report.

## Workflow

1. First, call `get_next_breed_to_research` to get the breed you should research
2. Call `get_breed_details` to see the current information we have on file
3. Use `web_search` to find authoritative sources about this breed:
   - Search for "<breed name> chicken breed characteristics"
   - Search for "<breed name> chicken egg production facts"
   - Search for "<breed name> chicken history origin"
   - Search for other specific aspects that need verification
4. Use `web_fetch` to read promising sources in detail
5. You may call up to 8 research tools (web_search + web_fetch combined)
6. After gathering sufficient information, call `save_breed_research` to record your findings

## Research Goals

For each breed, your report should cover:
- **History & Origin**: Where did this breed come from? When was it developed?
- **Physical Characteristics**: What makes this breed visually distinctive?
- **Temperament**: How do these chickens behave? Are they friendly, flighty, broody?
- **Egg Production**: Verify egg color, size, and annual production numbers
- **Unique Traits**: What makes this breed special or interesting compared to others?

## Quality Standards

- Verify each existing data field against your research
- If you find conflicting information, note the discrepancy and use the most authoritative source
- Prioritize sources like:
  - Poultry breed registries and associations
  - University agricultural extensions
  - Established chicken keeping communities (backyardchickens.com, mypetchicken.com)
  - Breed-specific clubs and organizations
- Always include source URLs for every fact you report

## Output Format

When calling `save_breed_research`, provide:
- A 2-4 paragraph report summarizing what makes this breed unique
- Updated values for any fields you can verify or improve
- A list of all source URLs you used

Remember: Your research will be used to update our breed database, so accuracy is critical.
""".trimIndent()
```

---

## Configuration

### File: `src/main/kotlin/co/qwex/chickenapi/config/BreedResearchAgentProperties.kt`

```kotlin
@ConfigurationProperties(prefix = "koog.breed-research-agent")
data class BreedResearchAgentProperties(
    val enabled: Boolean = true,
    val baseUrl: String = "https://ollama.com",
    val model: String = "gpt-oss:120b",
    val webSearchMaxResults: Int = 3,
    val maxAgentIterations: Int = 100,
    val maxToolCalls: Int = 8,
)
```

### application.properties additions

```properties
# Breed Research Agent
koog.breed-research-agent.enabled=true
koog.breed-research-agent.base-url=${koog.agent.base-url}
koog.breed-research-agent.model=${koog.agent.model}
koog.breed-research-agent.web-search-max-results=3
koog.breed-research-agent.max-agent-iterations=100
koog.breed-research-agent.max-tool-calls=8
```

> **Note:** The breed research agent reuses the shared Koog credentials (`koog.agent.api-key`).

---

## Scheduled Task Service

### File: `src/main/kotlin/co/qwex/chickenapi/service/BreedResearcherScheduledTaskService.kt`

```kotlin
@Service
class BreedResearcherScheduledTaskService(
    private val koogBreedResearchAgent: KoogBreedResearchAgent,
    private val breedRepository: BreedRepository,
    private val breedResearchRepository: BreedResearchRepository,
) {
    private val log = KotlinLogging.logger {}

    @Scheduled(fixedRate = 86400000)  // Every 24 hours
    fun runDailyBreedResearchTask() {
        log.info { "Breed Research scheduled task started" }

        if (!koogBreedResearchAgent.isReady()) {
            log.info { "Breed research agent not ready, skipping" }
            return
        }

        val runId = UUID.randomUUID().toString()
        val startedAt = Instant.now()

        val response = runBlocking {
            koogBreedResearchAgent.researchBreed()
        }

        // Parse response, update breed, save research record...
    }
}
```

---

## Implementation Tasks (Ordered)

### Phase 1: Data Model & Repository (Foundation)

- [ ] **1.1** Add `sources` field to `Breed` data class
- [ ] **1.2** Update `GoogleSheetBreedRepository` to read/write sources column (K)
- [ ] **1.3** Create `BreedResearchRecord` model
- [ ] **1.4** Create `BreedResearchRepository` interface
- [ ] **1.5** Implement `GoogleSheetBreedResearchRepository`
- [ ] **1.6** Add `breed_research_runs` sheet to Google Sheets with correct schema

### Phase 2: Tools (Building Blocks)

- [ ] **2.1** Create `GetNextBreedToResearchTool` with selection algorithm
- [ ] **2.2** Create `GetBreedDetailsTool`
- [ ] **2.3** Refactor `WebSearchTool` to be reusable (extract from KoogChickenFactsAgent)
- [ ] **2.4** Refactor `WebFetchTool` to be reusable (extract from KoogChickenFactsAgent)
- [ ] **2.5** Create `SaveBreedResearchTool`

### Phase 3: Agent Core (Orchestration)

- [ ] **3.1** Create `BreedResearchAgentProperties` configuration class
- [ ] **3.2** Add breed research agent config to `application.properties`
- [ ] **3.3** Create `BreedResearchStrategy` graph-based strategy
- [ ] **3.4** Create `KoogBreedResearchAgent` Spring service
- [ ] **3.5** Implement agent initialization and tool registration
- [ ] **3.6** Implement `researchBreed()` method

### Phase 4: Scheduling & Persistence (Integration)

- [ ] **4.1** Create `BreedResearcherScheduledTaskService`
- [ ] **4.2** Implement JSON parsing for agent output
- [ ] **4.3** Implement breed update logic
- [ ] **4.4** Implement research record persistence
- [ ] **4.5** Add error handling and retry logic

### Phase 5: Testing & Refinement

- [ ] **5.1** Unit tests for `GetNextBreedToResearchTool` selection logic
- [ ] **5.2** Unit tests for `SaveBreedResearchTool` output parsing
- [ ] **5.3** Integration test for full agent run
- [ ] **5.4** Manual testing with real breeds
- [ ] **5.5** Prompt tuning based on output quality

---

## API Endpoints (Future Enhancement)

```kotlin
@RestController
@RequestMapping("api/v1/breeds")
class BreedController {

    // Existing endpoints...

    @GetMapping("/{id}/research-history")
    fun getBreedResearchHistory(@PathVariable id: Int): List<BreedResearchRecord>

    @PostMapping("/{id}/research")
    fun triggerBreedResearch(@PathVariable id: Int): ResponseEntity<Void>
}
```

---

## Success Metrics

1. **Coverage**: All breeds should be researched within 30 days (30 breeds / 1 per day)
2. **Quality**: Reports should be 200-500 words with 3+ sources each
3. **Accuracy**: Spot-check 10% of updated breeds for factual accuracy
4. **Reliability**: Agent should complete successfully >95% of runs

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Web search API rate limits | Agent fails | Implement backoff, limit to 1 run/day |
| Poor quality sources | Inaccurate data | Prioritize known-good domains in prompts |
| Breed not found in searches | Empty results | Fallback to general chicken + breed name |
| Very long agent runs | Resource usage | Max iterations limit, timeout |
| Conflicting information | Data quality | Log discrepancies, prefer official sources |

---

## Dependencies

- Koog Agent Framework (`ai.koog:koog-agents:0.5.2`)
- Google Sheets API (existing)
- Ollama Cloud API (existing)
- Spring Boot Scheduling (existing)

---

## Timeline Estimate

This epic is estimated at **Medium complexity** (~2-3 weeks of development time for a single developer).

Phase 1-2 can be parallelized if multiple developers are available.
