# CP2: evaluation of the initial suite

Initial suite = what the agent produced in one pass (commit 5793274), nothing fixed or tuned yet.
Numbers are from my own Mac runs on 2026-11-07, screenshots in `evidence/`.

## 1. Coverage

| | Tests | Lines | Branches |
|---|---|---|---|
| Backend (JaCoCo) | 130 | 704/736 (95.7%) | 464/570 (81.4%) |
| Frontend (v8) | 111 | 291/356 (81.7%) | 345/455 (75.8%) |

Frontend files at 0%: `App.tsx`, `main.tsx`, `HomePage.tsx`, `PaperDetailPage.tsx` (the page that ties summary, Q&A and metadata editing together, so this is a real hole, not just boilerplate).

Backend uncovered code (from the JaCoCo line view), grouped by why it matters:

| Code | What is not exercised |
|---|---|
| `GlobalExceptionHandler` 36-37 | the 413 `FILE_TOO_LARGE` response (the documented 25 MB limit) |
| `LibraryService.save` 57-61, `UploadService` 82-86 | the "two requests saved the same paper at once" fallback (`DataIntegrityViolationException` then re-read) |
| `PdfExtractionService.LineCollector` 98-103 | wide-gap detection that inserts the column `GAP` marker (the thing the author/title heuristics rely on); only 11/24 branches |
| `PdfExtractionService` 74-76 | `TEXT_EXTRACTION_FAILED` 422 path |
| `PaperContentService` 66-68 | interrupted download; also the oversize and non-PDF-magic branches only partly covered |
| `PdfMetadataExtractor` | 130/182 branches: title-size heuristics, multi-line titles, junk info-titles |
| `UploadService` 47-48, 93-94 | IO failure on read, SHA-256 missing (the second one is unreachable on a real JVM, not worth a test) |

## 2. Correctness: passing is not the same as right

Failures (9 frontend, 3 backend). Classified by tracing each to its cause:

| Test | Verdict |
|---|---|
| `SearchApiTest.resultsWithoutIdsDoNotBreakTheSearch` | **APP BUG**: `Set.of().contains(null)` NPE, 500 instead of results |
| `TextChunkerTest.chunksRespectTheMaximumSize...` | **APP BUG (minor)**: MAX_CHARS is a soft cap, chunk of 1817 chars |
| `LibraryApiTest.listSearchMatches...` | **BAD TEST**: MockMvc double-encodes `%20%20` |
| 8 frontend tests (AskPanel, Layout, PaperCard, DiscoverPage, 2 each) | **BAD TEST**: `beforeEach` arrow returns the mock, Vitest runs it as cleanup |
| `LibraryPage` badges test | **BAD TEST**: `getByText` matches badge and `<option>` |

So 10 of 12 failures were wrong tests, 2 were wrong app. If I had "fixed the failures" blindly I would have edited the app to satisfy bad tests 10 times out of 12, which is the trap the assignment warns about.

Passing tests that are weak or misleading (read the bodies, not just the names):

| Test | Problem |
|---|---|
| `QaApiTest.groupedCitationsAreAllRecognised` | name says grouped citations, but the model reply is `[1, 1]`. A bug that only reads the first number of a group would still pass. Should be `[1, 3]` with two sources expected. |
| `QaApiTest.citationsToExcerptsThatDoNotExistAreIgnored` | checks `sources` is empty but not what happens to the `[9]` in the answer text |
| `LlmClientTest.apiKeyIsNeverIncludedInErrorMessages` | only a negative assertion on one 401 with an empty body, passes trivially if the message is just generic |
| `UploadApiTest.requestWithoutAFilePartIsABadRequest` | status code only, no check of the error envelope (`error.code`) |
| 6 `UploadApiTest`/`QaApiTest` error tests | check status but not the `code`/`retryable` the frontend actually reads |
| `PaperAndDtoTest` (5 tests) | getters and a text-column round trip, they add coverage numbers but little fault-finding value |

Redundancy: the "generic message for unexpected errors" test is repeated in AskPanel, SummaryPanel and PaperCard (same pattern, same assertion). Not wrong, but 3 tests for one idea.

## 3. Fault detection

Bugs found by the initial suite: 2 (above). Not found yet but suspected from the uncovered code: duplicate-save race and the 413 path, neither of which the suite can currently reach. No mutation testing run, so I cannot claim how many seeded faults the suite would miss.

## 4. Plan for the improvement rounds (two different strategies)

- **Round 1: feedback-driven.** Give the agent the 12 failures (with my triage), then the JaCoCo/v8 uncovered lines. Fix the 10 bad tests, add tests for the uncovered code above, add HomePage/PaperDetailPage/App tests. Bug tests stay failing/red-documented, app code is not changed to hide them.
- **Round 2: requirements and risk driven, not coverage driven.** Give the agent the HW1 requirements plus my weak-test list and ask for: stronger assertions (error envelope, exact citation behaviour), boundary and invalid-input probing per endpoint, and cross-layer contract tests (frontend `ApiError` fields vs the backend envelope). Measure by bugs found and assertion strength, not by percentage.

Open decision for the bugs: report only, or fix the two app bugs at the end with a regression test. Default: fix both in Round 2 with a clear before/after note, since the report asks to document bugs and a fixed one with a regression test is better evidence.
