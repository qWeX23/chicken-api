# EPIC 5 — Admin Review Queue (Approve Draft Breeds + Edits + Content)

**Title:** Epic: Admin review queue for agent-produced drafts (breeds/edits/content)  
**Labels:** epic, admin, quality  
**Milestone:** M4 - Quality + Review

## Goal
Introduce a human-in-the-loop review flow to approve/reject agent outputs before publication.

## Technical design

### Reviewable artifact types
- `BreedDraft`
- `ProposedEdit` (field-level updates to existing entities)
- `ContentCard` (pre-publication)

### Workflow states
- `PENDING_REVIEW`
- `NEEDS_MORE_SOURCES`
- `APPROVED`
- `REJECTED`
- `APPLIED` (for artifacts requiring downstream merge)

### Reviewer experience (v1)
- Queue list with filters (type, confidence, age, source count).
- Detail page showing:
  - proposed payload
  - side-by-side diff against canonical data
  - source links and confidence reasons
  - action history and prior reviewer notes

### Action model
- Approve: records reviewer + timestamp + rationale and applies change.
- Reject: records rejection reason and creates dedupe marker to prevent immediate re-submission.
- Needs more sources: pushes item back to agent with requested evidence category.

### Audit trail schema
- `review_event`:
  - artifact_id, artifact_type
  - previous_state, next_state
  - reviewer_id
  - rationale
  - field_diff_json
  - source_snapshot_json
  - created_at

## Acceptance criteria
- Can review pending drafts and apply `APPROVED` / `REJECTED`.
- Approval updates canonical dataset with provenance.
- Rejection reason is retained and prevents immediate duplicate resubmission.
- Every applied change is traceable.

## Child issues
- [ ] Define reviewable artifact types (BreedDraft, ProposedEdit, ContentCard)
- [ ] Implement review queue list view + detail view
- [ ] Add approve/reject actions + reviewer notes
- [ ] Apply approved changes to canonical storage
- [ ] Create audit trail schema (diff + sources)
- [ ] Add reopen/needs-more-sources state (optional)

## Risks and mitigations
- **Reviewer bottleneck:** prioritize by confidence + impact + age.
- **Insufficient context:** require source snapshots in detail view.
- **Inconsistent decisions:** use reviewer guidelines and templated rejection reasons.
