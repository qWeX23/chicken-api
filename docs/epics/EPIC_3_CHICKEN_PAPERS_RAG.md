# EPIC 3 — Research Papers → Embeddings + RAG (Chicken Papers v1)

**Title:** Epic: Chicken Papers RAG v1 — ingest papers, embed, search with citations  
**Labels:** epic, rag, data-ingestion, agent  
**Milestone:** M2 - Research RAG v1

## Goal
Build a searchable, citation-backed knowledge base from poultry research papers, enabling research-grounded answers and content.

## Technical design

### End-to-end flow
1. Discover papers (open-access sources first).
2. Resolve metadata and dedupe (`doi`, canonical URL hash).
3. Download PDF and store artifact + checksum.
4. Extract text (with OCR fallback where needed).
5. Chunk by document structure (title/abstract/methods/results/conclusion).
6. Generate embeddings per chunk and write to vector store.
7. Generate practical takeaway bullets per paper.
8. Serve retrieval endpoint with ranked chunks + citations.

### Data model (minimum)
- `paper`:
  - title, authors, year, journal, doi, url, license
  - ingestStatus, checksum, discoveredAt, ingestedAt
- `paper_chunk`:
  - paperId, section, chunkIndex, chunkText, tokenCount
  - embeddingVectorRef, citationText
- `paper_takeaway`:
  - paperId, bullets[], confidence, generatedAt

### Retrieval behavior
- Hybrid search: vector similarity + metadata filtering.
- Rerank top 30 candidates to top K (e.g., 5).
- Response includes:
  - chunk text snippet
  - citation fields (title/year/doi/url)
  - confidence and score breakdown

### Guardrails
- Max chunk count per query.
- Max token budget for context assembly.
- Domain allowlist and license checks for ingestion.
- Timeout budget per retrieval call.

## API design (v1)
- `POST /api/v1/research/papers/ingest` (manual ingest trigger)
- `GET /api/v1/research/papers/search?q=&topic=&k=`
- `GET /api/v1/research/papers/{paperId}` (metadata + takeaways)

## Acceptance criteria
- Ingest works end-to-end (PDF -> chunks -> embeddings -> searchable).
- Retrieval returns top chunks and complete citation metadata.
- Basic topic tagging exists (nutrition/disease/heat stress/etc.).
- Hard limits control cost/latency.

## Child issues
- [ ] Define paper metadata schema (title/authors/year/journal/doi/url/license)
- [ ] Implement paper discovery list (seed list + scheduled updates)
- [ ] PDF ingestion pipeline (download/store + text extraction)
- [ ] Chunking strategy (by headers/sections; fallback to size-based)
- [ ] Embedding generation + storage (pgvector/Supabase vector)
- [ ] Retrieval endpoint/tool (query -> top K -> return citations)
- [ ] Generate practical takeaway bullets per paper
- [ ] Add dedupe for papers (doi/url hash)
- [ ] Add evaluation harness (10–20 sample questions + expected sources)

## Risks and mitigations
- **PDF parsing quality:** maintain fallback parser + OCR path.
- **Embedding cost drift:** cache embeddings and throttle backfills.
- **Citation trust:** store raw source chunks with immutable paper checksum.
