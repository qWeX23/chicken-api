# EPIC 6 — Data Quality + Provenance (Contradictions, Completeness, Source Tracking)

**Title:** Epic: Data quality system — provenance, contradiction detection, completeness checks  
**Labels:** epic, quality, agent  
**Milestone:** M4 - Quality + Review

## Goal
Continuously improve data reliability by tracking source provenance per field, detecting contradictory claims, and measuring completeness.

## Technical design

### Provenance model
For key fields, store:
- `value`
- `sourceUrl` / `sourceType`
- `confidence`
- `verifiedAt`
- `verifiedBy` (`agent` or reviewer id)
- `evidenceSnippet`

### Contradiction engine (v1)
- Rule-based comparisons for top fields (e.g., egg color, weight range, temperament class).
- Detects:
  - mutually exclusive categorical values
  - numerical range conflicts beyond threshold
  - stale high-confidence values being replaced by weak evidence
- Emits `quality_flag` records with severity and recommended action.

### Completeness scoring
- Weighted score per breed and per fact group.
- Example field weights:
  - name (required): 0.25
  - origin: 0.15
  - eggColor: 0.15
  - temperament: 0.15
  - description: 0.10
  - imageUrl: 0.05
  - source coverage: 0.15
- Output score bucket: `LOW`, `MEDIUM`, `HIGH` completeness.

### Weekly quality report artifact
- Top contradictions by severity.
- Lowest-completeness breeds.
- Fields with missing provenance.
- Suggested actions and queue links.

### Integration points
- Pushes flags into admin review queue.
- Protects canonical data by preventing low-confidence overwrite of high-confidence fields.
- Supplies quality metrics to operations dashboard.

## Acceptance criteria
- Important fields support provenance metadata.
- Contradiction detector flags mismatched claims.
- Completeness score exists per breed/per fact profile.
- Weekly quality report is generated and reviewable.

## Child issues
- [ ] Add provenance model per field (source + confidence + last verified)
- [ ] Implement contradiction detection rules (start with 3–5 fields)
- [ ] Implement completeness scoring (required fields + weights)
- [ ] Create quality report artifact (top issues + suggested fixes)
- [ ] Integrate with review queue (flags appear on affected breeds)
- [ ] Add safeguards: do not overwrite higher-confidence data automatically

## Risks and mitigations
- **False positives in contradictions:** begin with strict, high-signal rules and expand gradually.
- **Provenance overhead:** confine v1 to high-impact fields first.
- **Score gaming:** include source-quality weighting, not just field presence.
