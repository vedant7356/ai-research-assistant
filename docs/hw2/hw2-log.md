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

## Checkpoints

- [x] CP0 setup and baseline (frontend 0 tests; backend `contextLoads` passes on H2)
- [x] CP1 initial suite generated and run (backend 130 tests / 3 fail, frontend 111 tests / 9 fail)
- [ ] CP2 initial evaluation
- [ ] CP3 improvement round 1
- [ ] CP4 improvement round 2
- [ ] CP5 final evaluation
- [ ] CP6 README + clean-clone test + push
- [ ] CP7 report + submit
