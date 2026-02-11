# EPIC 2 — Breed Radar (New Breed Discovery + Draft Pipeline)

**Title:** Epic: Breed Radar — discover missing breeds and create sourced draft records  
**Labels:** epic, agent, data-ingestion, quality  
**Milestone:** M1 - Data Expansion

## Goal
Continuously discover potential chicken breeds not in our database, build a breed dossier with sources, and create draft records for review.

## Technical design

### Pipeline stages
1. **Source collection**: crawl curated source adapters (associations, hatcheries, breeder catalogs).
2. **Candidate extraction**: parse breed names, aliases, origin, purpose, and distinguishing traits.
3. **Normalization**: canonicalize punctuation, locale variants, and transliterations.
4. **Dedupe**: compare against canonical breed table and alias graph.
5. **Scoring**: compute confidence with explainable factors.
6. **Draft creation**: emit `BreedDraft` with `PENDING_REVIEW` status.

### Breed dossier schema (proposed)
- `candidateName` (required)
- `canonicalName` (required)
- `aliases[]`
- `classification` (`standardized`, `landrace`, `cross`, `unknown`)
- `originCountry`
- `purpose[]` (egg, meat, dual-purpose, ornamental)
- `traits[]`
- `sourceCitations[]` (2+ required)
- `confidenceScore` (0-1)
- `confidenceReasons[]`
- `discoveredAt`

### Dedupe strategy
- Exact match on normalized name.
- Exact match on alias table.
- Fuzzy match (Jaro-Winkler + token set ratio) against canonical and aliases.
- Phonetic fallback (Double Metaphone-like key) for transliteration drift.
- Block low-confidence fuzzy hits for human review instead of auto-create.

### Confidence rubric (example)
- +0.30 appears on breed association site.
- +0.20 appears in at least two independent trusted sources.
- +0.20 stable alias mapping found.
- -0.25 marked as marketing hybrid only.
- -0.15 conflicting taxonomy across sources.

## Integrations
- Emits drafts to review queue (Epic 5).
- Feeds “new breeds discovered” content input (Epic 4).
- Writes run summary to orchestration run records (Epic 1).

## Acceptance criteria
- Produces new-breed candidates weekly/on demand.
- Each candidate has required dossier data and 2–3 sources.
- Dedupe prevents re-adding the same breed/alias.
- Drafts land in `PENDING_REVIEW`.

## Child issues
- [ ] Define `Breed Dossier` schema (fields + required sources)
- [ ] Implement source collectors (start with 2–3 trusted source types)
- [ ] Name normalization + alias model (canonical name + variants)
- [ ] Dedupe strategy (fuzzy match + alias table)
- [ ] Confidence scoring + reasons (standardized vs informal cross)
- [ ] Draft creation into storage (Sheets/DB) with `PENDING_REVIEW`
- [ ] Output run summary: candidates found, duplicates ignored, drafts created
- [ ] Add a small unit test set for normalization + dedupe

## Risks and mitigations
- **Source quality variance:** maintain allowlist + source weighting.
- **Alias explosion:** cap alias count and retain provenance for each alias.
- **Taxonomy ambiguity:** require reviewer decision when confidence is borderline.
