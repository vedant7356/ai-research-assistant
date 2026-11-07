# Homework 2 log: test generation and improvement

Working log for HW2. Everything here is filled in as it happens. Raw evidence (prompts, screenshots, coverage output) goes in `docs/hw2/evidence/`.

## Stack

| Part | Tests | Coverage |
|---|---|---|
| Backend (Spring Boot 4.1.1, Java 21) | JUnit 5, Mockito, Spring Boot test slices, H2 in-memory DB (`test` profile) | JaCoCo 0.8.13 |
| Frontend (React + TS, Vite) | Vitest 5, React Testing Library, jsdom | Vitest v8 coverage |

External services (OpenAlex, Purdue GenAI) are always mocked in tests.

## Commands

Backend (from `backend/`):

    ./mvnw clean test
    # HTML report: backend/target/site/jacoco/index.html
    # Summary:
    awk -F, 'NR>1{im+=$4;ic+=$5;bm+=$6;bc+=$7;lm+=$8;lc+=$9} END{printf "lines %.1f%% (%d/%d)  branches %.1f%% (%d/%d)\n", 100*lc/(lm+lc), lc, lm+lc, 100*bc/(bm+bc), bc, bm+bc}' target/site/jacoco/jacoco.csv

Frontend (from `frontend/`):

    npm install
    npm test                 # run once
    npm run test:coverage    # run + coverage (HTML: frontend/coverage/index.html)

## Rules I'm following

- The coding agent writes and improves the tests. Any manual edit is logged below.
- Tests are judged against the app's intended behaviour. I do not change the app to make a test pass.
- A failing test is classified as APP BUG or BAD TEST, with the reason.
- Numbers in the report come from runs on my machine, not estimates.

## Baseline (CP0)

| | Lines | Branches |
|---|---|---|
| Frontend (0 tests) | 0 / 356 (0%) | 0 / 455 (0%) |
| Backend (only the generated `contextLoads` test) | 76 / 736 (10.3%) | 1 / 570 (0.2%) |

## Manual interventions

- CP0: test profile needed `NON_KEYWORDS=YEAR` in the H2 URL because `year` is reserved in H2 2.x (test config only, app unchanged).

## CP1: initial suite (agent-generated, nothing fixed yet)

Prompt strategy: one-shot generation from reading the source (controllers, services, DTOs, client.ts, components). Asked for normal, edge and error cases per class, real assertions, OpenAlex and the LLM always mocked, H2 `test` profile, no key or URL that is real.

Suite size: backend 130 tests (129 new + `contextLoads`), frontend 111 tests in 9 files.

Run on my Mac, 2026-11-07 (Node v25.9 gives EBADENGINE warnings for jsdom/vitest, tests still run):

| | Tests | Passed | Failed | Lines | Branches |
|---|---|---|---|---|---|
| Backend (JaCoCo) | 130 | 127 | 3 | 704/736 (95.7%) | 464/570 (81.4%) |
| Frontend (v8) | 111 | 102 | 9 | 291/356 (81.7%) | 345/455 (75.8%) |

Baseline was backend 10.3% lines / 0.2% branches, frontend 0 / 0.

Failures (first-pass triage, details in CP2):

| Test | Verdict | Why |
|---|---|---|
| `SearchApiTest.resultsWithoutIdsDoNotBreakTheSearch` | APP BUG | `savedExternalIds` returns `Set.of()` when no id, then `saved.contains(null)` throws NPE, API returns 500 |
| `TextChunkerTest.chunksRespectTheMaximumSizeEvenWithoutSentenceEnds` | APP BUG (minor) | MAX_CHARS only checked after a whole line is appended, chunk reached 1817 > 1800 |
| `LibraryApiTest.listSearchMatchesTitleOrAuthorCaseInsensitively` | BAD TEST | MockMvc double-encodes `%20%20` in the URL template, query was the literal text; service trims blanks correctly |
| 8 frontend tests (AskPanel x2, Layout x2, PaperCard x2, DiscoverPage x2) | BAD TEST | `beforeEach(() => vi.mocked(x).mockReset())` returns the mock, Vitest treats a returned function as cleanup and calls it after the test, so leftover rejected/pending mocks fail the test. Confirmed in a scratch copy: braces fixed all 8 |
| `LibraryPage` "shows source, text status..." | BAD TEST | `getByText('Saved from search')` matches both the badge and the Source `<option>` |

Frontend uncovered at this point: App.tsx, main.tsx, HomePage.tsx, PaperDetailPage.tsx (0%), UploadPage 89.9% stmts, LibraryPage 94.4%.
Backend weakest: PdfMetadataExtractor 130/182 branches, PdfExtractionService.LineCollector 11/24, UploadService 39/47 lines, PaperContentService 19/24 branches.

Note: the frontend suite was run once in a sandbox copy before my Mac run (102 pass / 9 fail, same 9), failures left unfixed on purpose. Backend was never run before my Mac.

Backend/frontend coverage needs `-Dmaven.test.failure.ignore=true` / `--coverage.reportOnFailure` while tests fail (reports are skipped otherwise).

Evidence: `evidence/cp1-*.png`, `evidence/cp1-frontend-sandbox-run.txt`.

## CP3: improvement round 1 (strategy: feedback-driven)

Input given to the agent: the 12 failures with my triage, the JaCoCo/v8 uncovered lines, and my CP2 audit (`cp2-evaluation.md`).
Rule kept: the two app bugs were NOT hidden, their tests stay red.

What changed:
- Fixed the 10 bad tests (4 frontend `beforeEach` bodies, `LibraryPage` badge test scoped per card, backend blank-query test now uses `.param`).
- Backend, 15 new tests in 4 files: `GlobalExceptionHandlerTest` (413 and error mapping), `SaveRaceTest` (duplicate save / duplicate upload fallbacks, plus integrity error with nothing to fall back on is not swallowed), `PdfLineGapTest` (GAP marker, margin numbers), `PdfMetadataFallbackTest` (info-field fallbacks, junk titles, implausible year, 300-char cut, arXiv header skipped). New `TestPdfs.twoRunsOnOneLine` helper.
- Frontend, 30 new tests in 3 files: `HomePage`, `PaperDetailPage`, `App` routing.

Result, my Mac, 2026-11-07:

| | Tests | Passed | Failed | Lines | Branches |
|---|---|---|---|---|---|
| Backend before -> after | 130 -> 145 | 127 -> 143 | 3 -> 2 | 95.7% -> 97.7% (719/736) | 81.4% -> 83.3% (475/570) |
| Frontend before -> after | 111 -> 141 | 102 -> 141 | 9 -> 0 | 81.7% -> 98.0% (349/356) | 75.8% -> 94.5% (430/455) |

The 2 remaining backend failures are the real app bugs (null-id search NPE, chunk size soft cap).
All 15 new backend tests passed on their first run on the Mac (they had never been compiled before). The new frontend tests were first run in the sandbox copy, and passed on the Mac too.

Honest note: backend coverage was already high, so round 1 mostly bought correctness (fewer wrong tests) and not percentage. Frontend gained the most, from 4 files at 0%.
Still uncovered on the frontend: `main.tsx` (bootstrap, 1 line), `UploadPage` 89.9% stmts.

Evidence: `evidence/cp3-*.png`.

## CP4: improvement round 2 (strategy: requirements and fault hunting, not coverage)

Different from round 1 on purpose: round 1 chased failures and uncovered lines, round 2 asked "what would a hostile or odd input do, and would the tests notice if the code were wrong?"

Input to the agent: HW1 requirements (error envelope, untrusted paper text, library behaviour), my CP2 list of weak tests, and a rule to pick tests by risk and not by coverage.

What changed:
- Strengthened weak assertions: grouped citations now `[1, 3]` over three real excerpts (the old test used `[1, 1]` and would pass if only the first number of a group were read), duplicate citation collapse kept as its own test, answer text checked when a citation points nowhere, `error.retryable`/`message` asserted on the Qa and Upload error tests, API-key leak test now feeds hostile error bodies for 5 statuses and has a positive control that the key really was sent.
- `AdversarialApiTest` (17): malformed JSON, wrong content type, unknown route/method, non-numeric ids, whitespace questions, 500 never leaks internals, long/trimmed titles, blank external ids, duplicate save, sort orders, two prompt-injection tests.
- `ErrorContractTest` (backend, 5) + `contract.test.ts` (frontend, 6): both layers checked against one shared fixture `contract/error-envelope.json`.
- Regression tests for the three fixed bugs (chunker x3, summary fence x1, plus the existing red tests that now pass).

Bugs, found by the suite then fixed (3 small app edits):

| # | Bug | Found by | Fix |
|---|---|---|---|
| 1 | Search page where no result has an id returns 500 (`Set.of().contains(null)` NPE) | initial suite, `SearchApiTest` | `externalId != null` guard in `PaperSearchController` |
| 2 | `TextChunker` chunks can exceed MAX_CHARS (1817 > 1800); one huge line gives one huge chunk | initial suite, `TextChunkerTest` | flush before overflow, cut huge lines at word boundary |
| 3 | Text inside an uploaded PDF can contain `EXCERPTS>>>` / `PAPER TEXT>>>` and close the "this is data" fence, so its own text lands where the user's question goes (prompt-injection hardening gap) | round 2, `AdversarialApiTest.paperTextCannotCloseTheExcerptFence...` | `defang()` in `QaService`, same replacement in `SummaryService` |

Bug 3 was found only because round 2 was threat-driven. No coverage number would have flagged it, the line was already covered.

Results (my Mac, 2026-11-07), pre-fix run showed exactly 3 failures (the 3 bugs above), post-fix run 0:

| | Tests | Failed | Lines | Branches |
|---|---|---|---|---|
| Backend after round 1 -> after round 2 | 145 -> 172 | 2 -> 0 | 97.7% (719/736) -> 97.7% (732/749) | 83.3% -> 83.6% (488/584) |
| Frontend after round 1 -> after round 2 | 141 -> 147 | 0 -> 0 | 98.0% -> 98.3% (350/356) | 94.5% -> 94.5% (430/455) |

Coverage barely moved in round 2 and that is the point: the value was in assertion strength and the third bug, not the percentage.

Evidence: `evidence/cp4-backend-prefix-run-part*.png` (before fixes), `evidence/cp4-frontend-run-partOne.png` (shows the backend 172/0 run and the frontend 147 run), `evidence/cp4-frontend-run-partTwo.png` (coverage table).

Seeded-fault comparison (initial 10/21, round 2 16/21, final 21/21) is in `cp5-final-evaluation.md` and `mutation/results.md`.

## Checkpoints

- [x] CP0 setup and baseline (frontend 0 tests; backend `contextLoads` passes on H2)
- [x] CP1 initial suite generated and run (backend 130 tests / 3 fail, frontend 111 tests / 9 fail)
- [x] CP2 initial evaluation (see cp2-evaluation.md)
- [x] CP3 improvement round 1 (feedback-driven)
- [x] CP4 improvement round 2 (requirements and fault driven)
- [x] CP5 final evaluation (see cp5-final-evaluation.md)
- [ ] CP6 README + clean-clone test + push
- [ ] CP7 report + submit
