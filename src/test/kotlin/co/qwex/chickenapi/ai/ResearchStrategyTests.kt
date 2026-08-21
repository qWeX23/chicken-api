package co.qwex.chickenapi.ai

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.serialization.typeToken
import co.qwex.chickenapi.TestFixtures
import co.qwex.chickenapi.ai.tools.GetBreedDetailsTool
import co.qwex.chickenapi.ai.tools.GetNextBreedToResearchTool
import co.qwex.chickenapi.ai.tools.BreedResearchRunContext
import co.qwex.chickenapi.ai.tools.SaveBreedResearchTool
import co.qwex.chickenapi.ai.tools.WebSearchTool
import co.qwex.chickenapi.config.BreedResearchAgentProperties
import co.qwex.chickenapi.config.KoogAgentProperties
import co.qwex.chickenapi.model.Breed
import co.qwex.chickenapi.repository.BreedRepository
import co.qwex.chickenapi.service.ChickenFactJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ResearchStrategyTests {
    private val json = Json { ignoreUnknownKeys = true }
    private val model =
        LLModel(
            provider = LLMProvider.Ollama,
            id = "test-model",
            capabilities = listOf(LLMCapability.Tools, LLMCapability.Schema.JSON.Basic),
            contextLength = 8_192,
        )

    @Test
    fun `chicken strategy recovers from reasoning-only replies and restricts forced save tools`() {
        val saveTool = SaveChickenFactTool(
            duplicateCheckService =
                object : ChickenFactDuplicateCheckService {
                    override suspend fun checkFactForDuplicate(fact: String, sourceUrl: String?) =
                        FactDuplicateCheckResult(
                            hasHit = false,
                            threshold = 0.88,
                            topSimilarity = null,
                            matches = emptyList(),
                        )
                },
        )
        val webSearchTool = WebSearchTool(null, "http://unused", 1)
        val executor = QueuePromptExecutor(
            listOf(
                reasoning("I should call web_fetch even though it is unavailable."),
                reasoning("I will describe the call instead of making it."),
                toolCall(
                    tool = saveTool.name,
                    args =
                        """{"fact":"Chicks communicate before hatching.","sourceUrl":"https://example.com/chicks"}""",
                ),
            ),
        )
        val registry = ToolRegistry {
            tool(saveTool)
            tool(webSearchTool)
        }
        val agent = AIAgent(
            promptExecutor = executor,
            strategy = chickenResearchStrategy(saveTool, webSearchTool),
            toolRegistry = registry,
            agentConfig = agentConfig(KoogAgentProperties().maxAgentIterations),
        ) {}

        val result = runBlocking { agent.run("Find a fact") }
        val parsed = json.decodeFromString<ChickenFactJson>(result)

        assertEquals("Chicks communicate before hatching.", parsed.fact)
        assertEquals("https://example.com/chicks", parsed.sourceUrl)
        assertEquals(3, executor.requests.size)
        assertEquals(listOf(saveTool.name), executor.requests[2].tools.map(ToolDescriptor::name))
    }

    @Test
    fun `chicken strategy executes a corrected save returned after a duplicate result`() {
        var duplicateChecks = 0
        val saveTool = SaveChickenFactTool(
            duplicateCheckService =
                object : ChickenFactDuplicateCheckService {
                    override suspend fun checkFactForDuplicate(fact: String, sourceUrl: String?): FactDuplicateCheckResult {
                        duplicateChecks += 1
                        return FactDuplicateCheckResult(
                            hasHit = duplicateChecks == 1,
                            threshold = 0.88,
                            topSimilarity = if (duplicateChecks == 1) 0.95 else null,
                            matches = emptyList(),
                        )
                    }
                },
        )
        val searchTool = RecordingSearchTool()
        val executor = QueuePromptExecutor(
            listOf(
                toolCall(
                    saveTool.name,
                    """{"fact":"A duplicate fact.","sourceUrl":"https://example.com/duplicate"}""",
                ),
                toolCall(searchTool.name, """{"query":"a different chicken topic"}"""),
                toolCall(
                    saveTool.name,
                    """{"fact":"A corrected unique fact.","sourceUrl":"https://example.com/unique"}""",
                ),
            ),
        )
        val registry = ToolRegistry {
            tool(saveTool)
            tool(searchTool)
        }
        val agent = AIAgent(
            promptExecutor = executor,
            strategy = chickenResearchStrategy(saveTool, searchTool, maxToolCalls = 1),
            toolRegistry = registry,
            agentConfig = agentConfig(KoogAgentProperties().maxAgentIterations),
        ) {}

        val result = runBlocking { agent.run("Find a unique fact") }
        val parsed = json.decodeFromString<ChickenFactJson>(result)

        assertEquals("A corrected unique fact.", parsed.fact)
        assertEquals(2, duplicateChecks)
        assertEquals(1, searchTool.calls)
        assertEquals(3, executor.requests.size)
    }

    @Test
    fun `chicken strategy forces a save after an over-budget non-save call`() {
        var duplicateChecks = 0
        val saveTool = SaveChickenFactTool(
            duplicateCheckService =
                object : ChickenFactDuplicateCheckService {
                    override suspend fun checkFactForDuplicate(fact: String, sourceUrl: String?): FactDuplicateCheckResult {
                        duplicateChecks += 1
                        return FactDuplicateCheckResult(
                            hasHit = duplicateChecks == 1,
                            threshold = 0.88,
                            topSimilarity = if (duplicateChecks == 1) 0.95 else null,
                            matches = emptyList(),
                        )
                    }
                },
        )
        val searchTool = RecordingSearchTool()
        val executor = QueuePromptExecutor(
            listOf(
                toolCall(
                    saveTool.name,
                    """{"fact":"A duplicate fact.","sourceUrl":"https://example.com/duplicate"}""",
                ),
                toolCall(searchTool.name, """{"query":"another chicken fact"}"""),
                toolCall(searchTool.name, """{"query":"one more chicken fact"}"""),
                toolCall(searchTool.name, """{"query":"an unavailable extra search"}"""),
                toolCall(
                    saveTool.name,
                    """{"fact":"A forced unique fact.","sourceUrl":"https://example.com/forced"}""",
                ),
            ),
        )
        val registry = ToolRegistry {
            tool(saveTool)
            tool(searchTool)
        }
        val agent = AIAgent(
            promptExecutor = executor,
            strategy = chickenResearchStrategy(saveTool, searchTool, maxToolCalls = 0),
            toolRegistry = registry,
            agentConfig = agentConfig(KoogAgentProperties().maxAgentIterations),
        ) {}

        val result = runBlocking { agent.run("Find a unique fact") }
        val parsed = json.decodeFromString<ChickenFactJson>(result)

        assertEquals("A forced unique fact.", parsed.fact)
        assertEquals(2, duplicateChecks)
        assertEquals(1, searchTool.calls)
        assertEquals(5, executor.requests.size)
        assertEquals(listOf(searchTool.name), executor.requests[1].tools.map(ToolDescriptor::name))
        assertEquals(listOf(saveTool.name), executor.requests[3].tools.map(ToolDescriptor::name))
        assertEquals(listOf(saveTool.name), executor.requests[4].tools.map(ToolDescriptor::name))
    }

    @Test
    fun `chicken strategy resets the tool budget after a duplicate`() {
        var duplicateChecks = 0
        val saveTool = SaveChickenFactTool(
            duplicateCheckService =
                object : ChickenFactDuplicateCheckService {
                    override suspend fun checkFactForDuplicate(fact: String, sourceUrl: String?): FactDuplicateCheckResult {
                        duplicateChecks += 1
                        return FactDuplicateCheckResult(
                            hasHit = duplicateChecks == 1,
                            threshold = 0.88,
                            topSimilarity = if (duplicateChecks == 1) 0.95 else null,
                            matches = emptyList(),
                        )
                    }
                },
        )
        val searchTool = RecordingSearchTool()
        val executor = QueuePromptExecutor(
            listOf(
                toolCall(
                    saveTool.name,
                    """{"fact":"A duplicate fact.","sourceUrl":"https://example.com/duplicate"}""",
                ),
                toolCall(searchTool.name, """{"query":"a genuinely different topic"}"""),
                toolCall(
                    saveTool.name,
                    """{"fact":"A unique researched fact.","sourceUrl":"https://example.com/unique"}""",
                ),
            ),
        )
        val registry = ToolRegistry {
            tool(saveTool)
            tool(searchTool)
        }
        val agent = AIAgent(
            promptExecutor = executor,
            strategy = chickenResearchStrategy(saveTool, searchTool, maxToolCalls = 1),
            toolRegistry = registry,
            agentConfig = agentConfig(KoogAgentProperties().maxAgentIterations),
        ) {}

        val result = runBlocking { agent.run("Find a unique fact") }
        val parsed = json.decodeFromString<ChickenFactJson>(result)

        assertEquals("A unique researched fact.", parsed.fact)
        assertEquals(1, searchTool.calls)
        assertEquals(2, duplicateChecks)
    }

    @Test
    fun `breed strategy completes select details and save workflow`() {
        val repository = RecordingBreedRepository(TestFixtures.breedNeverUpdated1)
        val runContext = BreedResearchRunContext()
        val getNextBreedTool = GetNextBreedToResearchTool(repository, runContext)
        val getBreedDetailsTool = GetBreedDetailsTool(repository, runContext)
        val saveTool = SaveBreedResearchTool(repository, runContext)
        val executor = QueuePromptExecutor(
            listOf(
                toolCall(getNextBreedTool.name, """{"fetch":true}"""),
                toolCall(getBreedDetailsTool.name, """{"breedId":1}"""),
                toolCall(
                    saveTool.name,
                    """{"breedId":1,"description":"A hardy, friendly dual-purpose breed.","origin":"USA","eggColor":"Brown","eggSize":"Large","temperament":"Friendly","numEggs":250,"sources":["https://example.com/rhode-island-red"]}""",
                ),
            ),
        )
        val registry = ToolRegistry {
            tool(getNextBreedTool)
            tool(getBreedDetailsTool)
            tool(saveTool)
        }
        val agent = AIAgent(
            promptExecutor = executor,
            strategy = breedResearchStrategy(saveTool, maxToolCalls = 2),
            toolRegistry = registry,
            agentConfig = agentConfig(BreedResearchAgentProperties().maxAgentIterations),
        ) {}

        val result = runBlocking { agent.run("Research a breed") }
        val parsed = json.decodeFromString<SaveBreedResearchTool.Result>(result)

        assertEquals(true, parsed.success)
        assertEquals(1, parsed.breedId)
        assertEquals(3, executor.requests.size)
        assertNotNull(repository.updatedBreed)
    }

    @Test
    fun `breed strategy retries a save after a failed save_breed_research call`() {
        val repository = RecordingBreedRepository(TestFixtures.breedNeverUpdated1)
        val runContext = BreedResearchRunContext()
        val getNextBreedTool = GetNextBreedToResearchTool(repository, runContext)
        val saveTool = SaveBreedResearchTool(repository, runContext)
        val badSaveArgs =
            """{"breedId":1,"description":"A hardy breed.","sources":[{"title":"Example","url":"https://example.com/source"}]}"""
        val goodSaveArgs =
            """{"breedId":1,"description":"A hardy breed.","sources":["https://example.com/source"]}"""
        val executor =
            QueuePromptExecutor(
                listOf(
                    toolCall(getNextBreedTool.name, """{"fetch":true}"""),
                    toolCall(saveTool.name, badSaveArgs),
                    toolCall(saveTool.name, goodSaveArgs),
                ),
            )
        val registry =
            ToolRegistry {
                tool(getNextBreedTool)
                tool(saveTool)
            }
        val agent =
            AIAgent(
                promptExecutor = executor,
                strategy = breedResearchStrategy(saveTool),
                toolRegistry = registry,
                agentConfig = agentConfig(BreedResearchAgentProperties().maxAgentIterations),
            ) {}

        val result = runBlocking { agent.run("Research a breed") }
        val parsed = json.decodeFromString<SaveBreedResearchTool.Result>(result)

        assertEquals(true, parsed.success)
        assertEquals(1, parsed.breedId)
        assertEquals(3, executor.requests.size)
        assertNotNull(repository.updatedBreed)
    }

    @Test
    fun `breed strategy rejects batched saves without writing`() {
        val repository = RecordingBreedRepository(TestFixtures.breedNeverUpdated1)
        val runContext = BreedResearchRunContext()
        val getNextBreedTool = GetNextBreedToResearchTool(repository, runContext)
        val saveTool = SaveBreedResearchTool(repository, runContext)
        val saveArgs =
            """{"breedId":1,"description":"A hardy breed.","sources":["https://example.com/source"]}"""
        val executor = QueuePromptExecutor(
            listOf(
                toolCall(getNextBreedTool.name, """{"fetch":true}"""),
                toolCalls(
                    MessagePart.Tool.Call(id = "save-1", tool = saveTool.name, args = saveArgs),
                    MessagePart.Tool.Call(id = "save-2", tool = saveTool.name, args = saveArgs),
                ),
            ),
        )
        val registry = ToolRegistry {
            tool(getNextBreedTool)
            tool(saveTool)
        }
        val agent = AIAgent(
            promptExecutor = executor,
            strategy = breedResearchStrategy(saveTool),
            toolRegistry = registry,
            agentConfig = agentConfig(BreedResearchAgentProperties().maxAgentIterations),
        ) {}

        val result = runBlocking { agent.run("Research a breed") }

        assertEquals("{}", result)
        assertEquals(null, repository.updatedBreed)
    }

    @Test
    fun `breed strategy forces a save after an over-budget non-save call`() {
        val repository = RecordingBreedRepository(TestFixtures.breedNeverUpdated1)
        val runContext = BreedResearchRunContext()
        val getNextBreedTool = GetNextBreedToResearchTool(repository, runContext)
        val getBreedDetailsTool = GetBreedDetailsTool(repository, runContext)
        val searchTool = RecordingSearchTool()
        val saveTool = SaveBreedResearchTool(repository, runContext)
        val saveArgs =
            """{"breedId":1,"description":"A hardy breed with a distinctive history.","sources":["https://example.com/source"]}"""
        val executor = QueuePromptExecutor(
            listOf(
                toolCall(getNextBreedTool.name, """{"fetch":true}"""),
                toolCall(getBreedDetailsTool.name, """{"breedId":1}"""),
                toolCall(searchTool.name, """{"query":"more breed evidence"}"""),
                toolCall(searchTool.name, """{"query":"one unavailable extra search"}"""),
                toolCall(saveTool.name, saveArgs),
            ),
        )
        val registry = ToolRegistry {
            tool(getNextBreedTool)
            tool(getBreedDetailsTool)
            tool(searchTool)
            tool(saveTool)
        }
        val agent = AIAgent(
            promptExecutor = executor,
            strategy = breedResearchStrategy(saveTool, maxToolCalls = 2),
            toolRegistry = registry,
            agentConfig = agentConfig(BreedResearchAgentProperties().maxAgentIterations),
        ) {}

        val result = runBlocking { agent.run("Research a breed") }
        val parsed = json.decodeFromString<SaveBreedResearchTool.Result>(result)

        assertEquals(true, parsed.success)
        assertEquals(0, searchTool.calls)
        assertEquals(5, executor.requests.size)
        assertEquals(listOf(saveTool.name), executor.requests[3].tools.map(ToolDescriptor::name))
        assertEquals(listOf(saveTool.name), executor.requests[4].tools.map(ToolDescriptor::name))
        assertNotNull(repository.updatedBreed)
    }

    private fun agentConfig(maxAgentIterations: Int) =
        AIAgentConfig(
            prompt = prompt("test-prompt") { system("Use tools") },
            model = model,
            maxAgentIterations = maxAgentIterations,
        )

    private fun reasoning(content: String) =
        Message.Assistant(
            parts = listOf(MessagePart.Reasoning(content)),
            metaInfo = ResponseMetaInfo.Empty,
        )

    private fun toolCall(tool: String, args: String) =
        Message.Assistant(
            part = MessagePart.Tool.Call(id = "call-$tool", tool = tool, args = args),
            metaInfo = ResponseMetaInfo.Empty,
        )

    private fun toolCalls(vararg calls: MessagePart.Tool.Call) =
        Message.Assistant(
            parts = calls.toList(),
            metaInfo = ResponseMetaInfo.Empty,
        )

    private class RecordingBreedRepository(initialBreed: Breed) : BreedRepository {
        private var breed = initialBreed
        var updatedBreed: Breed? = null
            private set

        override fun getAllBreeds(): List<Breed> = listOf(breed)

        override fun getBreedById(id: Int): Breed? = breed.takeIf { it.id == id }

        override fun update(entity: Breed) {
            breed = entity
            updatedBreed = entity
        }
    }

    private class RecordingSearchTool : SimpleTool<RecordingSearchTool.Args>(
        argsType = typeToken<Args>(),
        name = "web_search",
        description = "Searches for a different topic",
    ) {
        @Serializable
        data class Args(val query: String)

        var calls = 0
            private set

        override suspend fun execute(args: Args): String {
            calls += 1
            return "A result for ${args.query}"
        }
    }

    private class QueuePromptExecutor(responses: List<Message.Assistant>) : PromptExecutor() {
        data class Request(
            val prompt: Prompt,
            val model: LLModel,
            val tools: List<ToolDescriptor>,
        )

        private val responses = ArrayDeque(responses)
        val requests = mutableListOf<Request>()

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): Message.Assistant {
            requests += Request(prompt, model, tools.toList())
            return responses.removeFirstOrNull() ?: error("No scripted LLM response remains")
        }

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): Flow<StreamFrame> = error("Streaming is not used by this test")

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
            error("Moderation is not used by this test")

        override fun close() = Unit
    }
}
