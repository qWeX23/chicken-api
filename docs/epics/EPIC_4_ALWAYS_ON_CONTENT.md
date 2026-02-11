# EPIC 4 — Always-On Content Engine (Daily Tips / Spotlights / Myth-Busters)

**Title:** Epic: Always-On Content — generate daily/weekly chicken content that feels alive  
**Labels:** epic, content, agent  
**Milestone:** M3 - Always-On Content

## Goal
Produce steady, high-quality content cards (daily tips, breed spotlights, myth-busters) sourced from internal breed data and research paper outputs.

## Technical design

### Content production architecture
- **Planner**: creates a 14-day schedule balancing card types.
- **Generators**: type-specific prompt/rule modules.
- **Validator**: checks length, citation rules, prohibited claims, freshness.
- **Publisher**: writes approved cards to feed store and cache.

### `ContentCard` schema (v1)
- `id`
- `cardType` (`DAILY_TIP`, `BREED_SPOTLIGHT`, `MYTH_BUSTER`, `WEEKLY_RECAP`)
- `title`
- `shortBody`
- `longBody` (optional)
- `tags[]`
- `sourceRefs[]` (required for myth-busters and research-backed claims)
- `topicKey` and `entityKey` (for cooldown dedupe)
- `scheduledFor`
- `status` (`DRAFT`, `READY`, `DISABLED`, `PUBLISHED`)

### Rotation and dedupe rules
- Topic cooldown (e.g., 14 days).
- Breed spotlight cooldown per breed (e.g., 45 days).
- Reject near-duplicate cards via semantic similarity threshold.
- Enforce category distribution target (e.g., tips 40%, spotlights 35%, myth-busters 20%, recap 5%).

### Source policy
- Cards with scientific claims should include citation links from RAG store.
- Non-cited cards must remain practical/observational and avoid medical diagnosis language.

## Delivery endpoints
- `GET /api/v1/content/feed?start=&days=`
- `POST /api/v1/content/generate?daysAhead=14`
- `PATCH /api/v1/content/types/{type}/enabled` (admin toggle)

## Acceptance criteria
- Generates feed for at least 14 days ahead.
- Cards include required fields and sources when applicable.
- Content is deduped and category-rotated.
- Cards can render in app UI list view.

## Child issues
- [ ] Define `ContentCard` schema + storage
- [ ] Implement daily tip generator (seasonal rules + optional paper citations)
- [ ] Implement breed spotlight generator (uses breed DB + fun facts)
- [ ] Implement myth-buster generator (prefers paper citations when available)
- [ ] Add dedupe/rotation logic (cooldown per topic/breed)
- [ ] Create minimal content feed endpoint + UI view
- [ ] Add admin toggle to disable a card type

## Risks and mitigations
- **Stale content:** pre-generate rolling windows + regeneration jobs.
- **Repetitive voice:** prompt variation templates and semantic dedupe.
- **Low-quality claims:** mandatory validator stage before publish.
