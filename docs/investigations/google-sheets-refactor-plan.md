# Google Sheets refactor implementation plan

## Goal
Refactor Google Sheets access into a clean, idiomatic abstraction that:
- Provides a **common interface** for all reads/writes.
- Encapsulates **range construction** and **row mapping**.
- Supports a **table abstraction** per sheet.
- Automatically **creates missing sheets** when a table is accessed.

This plan is intentionally broken into small, safe, incremental tasks to reduce risk and ease review.

## Scope (Phase 1)
- Introduce a Google Sheets gateway abstraction and table descriptor system.
- Update **one feature path** end-to-end as a reference implementation (recommended: `BreedRepository`).
- Implement **"ensure sheet exists"** behavior when using a table.
- Maintain current API behavior and ensure tests pass.

## Non-goals (Phase 1)
- Refactor all repositories and services at once.
- Change endpoint behavior or response formats.
- Add caching or retries beyond the current behavior.

---

## Architecture outline (target design)

### 1) Core interfaces and data types
- `SheetsGateway`
  - `getValues(range: SheetRange, renderOption: ValueRenderOption = FORMATTED_VALUE): List<List<Any?>>`
  - `appendValues(range: SheetRange, rows: List<List<Any?>>, input: ValueInputOption = USER_ENTERED)`
  - `updateValues(range: SheetRange, rows: List<List<Any?>>, input: ValueInputOption = USER_ENTERED)`
  - `ensureTableExists(table: SheetTable<*>)`

- `SheetRange`
  - Value type for A1 range generation with proper sheet name quoting.
  - Example factory methods:
    - `SheetRange.columns(sheet: String, min: String, max: String)`
    - `SheetRange.row(sheet: String, min: String, max: String, row: Int)`
    - `SheetRange.headerRow(sheet: String, min: String, max: String)`

- `RowMapper<T>`
  - `fun map(row: List<Any?>): T?`

- `SheetTable<T>`
  - `val name: String`
  - `val columns: SheetColumns` (min/max, header row)
  - `val mapper: RowMapper<T>`
  - `fun headerRow(): List<Any?>`
  - Range helpers:
    - `headerRange()` (e.g., `'Breeds'!A1:K1`)
    - `dataRange()` (e.g., `'Breeds'!A2:K`)
    - `appendRange()` (e.g., `'Breeds'!A:K`)
    - `rowRange(n)` (e.g., `'Breeds'!A5:K5`)

### 2) Google Sheets adapter
- `GoogleSheetsGateway` implements `SheetsGateway`
  - Owns the `Sheets` client and `spreadsheetId`.
  - Centralizes `ValueRange` creation and `USER_ENTERED` input option.
  - Locks down value rendering in `getValues` (default `FORMATTED_VALUE`).
  - Adds `ensureTableExists` logic:
    - Use a small internal adapter (see below) to list sheet titles.
    - Cache known sheet titles in a `ConcurrentHashMap` to avoid repeated list calls.
    - If missing, call `batchUpdate` with `AddSheetRequest`.
    - Write headers with `updateValues` to `table.headerRange()` (deterministic range).
    - Handle concurrent creation by catching the specific Google API error for “already exists” and rethrow all other errors with context.

### 3) Tables and repositories
- `BreedsTable` implements `SheetTable<Breed>`
  - `name = "breeds"`
  - columns: `A` to `K`
  - header row values (for readability and initial creation)
- `GoogleSheetsBreedRepository` uses:
  - `SheetsGateway` + `BreedsTable`
  - `ensureTableExists(table)` before reads/writes

---

## Task breakdown (incremental)

### Task 1 — Add new packages and core types
**Goal:** Introduce framework types without changing runtime behavior.

1. Create package `co.qwex.chickenapi.repository.sheets`.
2. Add `SheetRange`, `SheetColumns`, `RowMapper<T>`, `SheetTable<T>`.
3. Implement sheet-name quoting in `SheetRange`:
   - Wrap names in single quotes.
   - Escape embedded single quotes by doubling them (e.g., `Bob's` → `'Bob''s'`).
4. Add unit tests for `SheetRange` to verify A1 formatting, quoting, and special characters (no gateway usage yet).

**Status:** ✅ Completed (core types added with quoting + SheetRange tests).

**Validation:** `./gradlew test` (Java 21).

---

### Task 2 — Implement `SheetsGateway` with Google Sheets adapter
**Goal:** Centralize Google Sheets API usage behind an interface.

1. Create `SheetsGateway` interface (methods above).
2. Add a tiny internal adapter (e.g., `SheetsClient`) to avoid deep Google client mocks:
   - `listSheetTitles(spreadsheetId): Set<String>`
   - `addSheet(spreadsheetId, sheetName)`
   - `getValues(spreadsheetId, a1Range, renderOption)`
   - `appendValues(spreadsheetId, a1Range, valueRange)`
   - `updateValues(spreadsheetId, a1Range, valueRange)`
3. Implement `GoogleSheetsGateway` using the adapter:
   - Construct `ValueRange` and set input options.
   - Map `SheetRange` to A1 (quoted sheet names).
   - Lock down `valueRenderOption` (default `FORMATTED_VALUE`).
   - Add `ensureTableExists`:
     - Memoize known sheets in a `ConcurrentHashMap`.
     - On miss, call `listSheetTitles`; if still missing, call `addSheet`.
     - If the add fails with “already exists”, treat as success; rethrow all other errors with context.
     - After creation, write headers using `updateValues(table.headerRange(), listOf(table.headerRow()))`.
4. Wire `GoogleSheetsGateway` as a Spring bean.

**Status:** ✅ Completed (gateway, adapter, and Spring wiring added with ensure-table-exists behavior).

**Validation:** Unit tests using the adapter to verify `ensureTableExists` behavior.

---

### Task 3 — Add table descriptor for breeds
**Goal:** Provide the first table definition and a reference implementation.

1. Create `BreedsTable` implementing `SheetTable<Breed>`:
   - Sheet name: `breeds`
   - Columns: `A` to `K`
   - Optional header row values: id, name, origin, eggColor, eggSize, temperament, description, imageUrl, numEggs, updatedAt, sources
2. Create `BreedRowMapper`:
   - Extract the same fields as current `GoogleSheetBreedRepository`.
   - Move parsing helpers to `RowParsing.kt`.
   - Define mapper behavior on invalid rows (recommended: skip invalid rows and log at debug/warn).
   - Parsing helpers must handle:
     - short rows (missing trailing cells)
     - blank cells
     - mixed cell types (Double/Boolean/String)

**Validation:** Unit tests for `BreedRowMapper` using sample rows.

**Status:** ✅ Completed (breeds table + row mapper with parsing helpers + tests).

---

### Task 4 — Refactor `GoogleSheetBreedRepository` to gateway + table
**Goal:** Convert a single repository to the new abstraction.

1. Rename class to `GoogleSheetsBreedRepository` (if not already) for clarity.
2. Replace direct `Sheets` usage with `SheetsGateway`.
3. Use `BreedsTable` to:
   - build ranges
   - map rows
   - generate header
4. Call `ensureTableExists(table)` before any read/write.

**Validation:** Update existing repository tests to use `SheetsGateway` or provide a fake.

**Status:** ✅ Completed (repository migrated to SheetsGateway with FakeSheetsGateway tests).

---

### Task 5 — Provide a test-friendly gateway
**Goal:** Make tests simple and avoid deep stubs.

1. Add `FakeSheetsGateway` for tests:
   - In-memory map keyed by `SheetRange` or `sheetName`.
   - Supports `getValues`, `appendValues`, `updateValues`, and `ensureTableExists`.
2. Unit-test `GoogleSheetsGateway` using the small adapter interface (no deep stubs).
3. Update `GoogleSheetsBreedRepository` tests to use the fake.

**Validation:** `./gradlew test` (Java 21).

**Status:** ✅ Completed (FakeSheetsGateway and focused gateway tests added).

---

## Auto-create sheet behavior

### Expected behavior
- Whenever a `SheetTable` is accessed (read or write), the gateway should ensure the sheet exists.
- If missing, the gateway should:
  1. Create the sheet via `AddSheetRequest`.
  2. Write a header row using `updateValues` to the deterministic `headerRange`.

### Edge cases
- **Concurrent creation:** If two requests attempt to create the same sheet at once, one may fail with a “already exists” error. The gateway should catch the specific Google API exception for that case only and continue.
- **Permissions:** If credentials do not allow sheet creation, the gateway should log a clear warning and surface a meaningful exception.
- **Header duplication:** Only write headers on creation; if the sheet exists, do not re-write headers.

---

## What else we need to ensure this works

1. **Centralized logging:**
   - Use `mu.KotlinLogging` for gateway diagnostics (already required by repo policy).

2. **Spring wiring:**
   - Ensure the `Sheets` bean remains in `GoogleSheetsConfig`.
   - Add `GoogleSheetsGateway` as a bean that depends on `Sheets` and `spreadsheetId`.

3. **Gradle + Java 21:**
   - All formatting and tests must run under Java 21 per repo guidance.

4. **Non-breaking migration:**
   - Convert repositories one at a time, starting with `BreedRepository` as the reference.
   - Keep current endpoints and data models unchanged.

5. **Testing strategy:**
   - Unit tests for the new range/mapping utilities (including sheet-name quoting).
   - Tests for parsing helpers with short rows and mixed types.
   - A focused test for `ensureTableExists` behavior and “already exists” errors.
   - Existing controller/service tests should continue to pass without modification once wired.

---

## Deliverables checklist (Phase 1)
- [x] `SheetRange`, `RowMapper`, and `SheetTable` types added.
- [x] `SheetsGateway` + `GoogleSheetsGateway` implemented.
- [x] `ensureTableExists` with `AddSheetRequest` and header insertion via `updateValues`.
- [x] `BreedsTable` + `BreedRowMapper` implemented.
- [x] `GoogleSheetsBreedRepository` migrated.
- [x] Tests updated to use `FakeSheetsGateway`.
- [x] All tests passing on Java 21.

---

## Suggested sequencing for review
1. Core types + `SheetsGateway` interface (no behavior change).
2. Gateway implementation + ensure-table-exists behavior.
3. Breeds table + mapper.
4. Repository migration + test updates.

This sequencing keeps each PR small and reviewable.
