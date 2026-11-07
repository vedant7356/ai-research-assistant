# CP5: final evaluation, initial vs final

All numbers are from my own runs on my Mac on 2026-11-07 (screenshots in `evidence/`).
"Initial" = the suite exactly as the agent first generated it (commit 5793274). "Final" = after improvement round 1, round 2 and the seeded-fault follow-up.

## 1. Size and pass/fail

| | Initial | After round 1 | Final |
|---|---|---|---|
| Backend tests | 130 | 145 | 177 |
| Backend failing | 3 | 2 | 0 |
| Frontend tests | 111 | 141 | 147 |
| Frontend failing | 9 | 0 | 0 |
| Total tests | 241 | 286 | 324 |

## 2. Coverage

| | Initial | After round 1 | Final |
|---|---|---|---|
| Backend lines | 704/736 (95.7%) | 719/736 (97.7%) | 732/749 (97.7%) |
| Backend branches | 464/570 (81.4%) | 475/570 (83.3%) | 490/584 (83.9%) |
| Frontend lines | 291/356 (81.7%) | 349/356 (98.0%) | 350/356 (98.3%) |
| Frontend branches | 345/455 (75.8%) | 430/455 (94.5%) | 430/455 (94.5%) |
| Frontend files at 0% | 4 (App, main, HomePage, PaperDetailPage) | 1 (main) | 1 (main, 1 bootstrap line) |

Before any tests: backend 76/736 lines (10.3%), 1/570 branches (0.2%); frontend 0%.

What did not improve: backend coverage was already 95.7% when the agent first generated the suite, so rounds 1 and 2 moved it by 2 points. Frontend gained 16 points of lines and 19 of branches, nearly all in round 1.
Remaining gap: `main.tsx` (React bootstrap, not worth a test), `UploadPage` 89.9% statements, and 94 backend branches, mostly in `PdfMetadataExtractor` heuristics (many individually-unlikely `if` combinations).

## 3. Test correctness

| | Initial | Final |
|---|---|---|
| Failing tests that were wrong tests | 10 (8 frontend `beforeEach` bug, 1 duplicate-text query, 1 URL double-encoding) | 0 |
| Failing tests that were real app bugs | 2 | 0 (both fixed, tests kept as regression tests) |
| Passing but weak or misleading tests found by review | 5 notable (grouped-citation test used `[1, 1]`, key-leak test trivially passes, error tests not checking `retryable`/message, status-only upload check, getter-only DTO tests) | strengthened or replaced |

A passing test is not necessarily right: the `[1, 1]` citation test passed on the initial suite and would also have passed if only the first number in a citation group were read.

## 4. Fault detection

Real bugs found in the HW1 app (all fixed, each with a regression test):

| # | Bug | Found in |
|---|---|---|
| 1 | Search page with no result ids returns HTTP 500 (`Set.of().contains(null)`) | initial suite |
| 2 | Text chunks can exceed the 1800-character maximum; one huge line becomes one huge chunk | initial suite |
| 3 | Text inside an uploaded PDF can contain the closing fence marker (`EXCERPTS>>>`, `PAPER TEXT>>>`) and escape the "this is data, not instructions" boundary of the LLM prompt | round 2 (threat-driven test) |

Seeded faults: 21 one-line bugs planted one at a time in a throw-away copy of the backend (`mutation/seeded_faults.py`). "Caught" = a test that passed before now fails.

| Suite | Faults caught |
|---|---|
| Initial | 10 of 21 |
| After round 2 | 16 of 21 |
| Final (after turning the 5 survivors into tests) | 21 of 21 |

The 5 faults the round 2 suite missed, and what test closed each:

| Fault | Closed by |
|---|---|
| M02 whole-paper threshold `<=` to `<` | paper of exactly 30,000 characters must still be sent whole |
| M04 source excerpt cut at 900 instead of 500 | excerpt must be exactly 500 characters plus an ellipsis |
| M09 scanned-PDF text minimum 100 to 10 | PDF with 99 characters must be rejected |
| M10 empty-PDF limit `<` to `<=` | PDF with exactly 100 characters must be accepted |
| M15 year picks the latest year instead of the earliest | page with 2021 and 2023 must give 2021 |

Cross-layer contract: backend and frontend are both checked against the same fixture `contract/error-envelope.json` (5 error shapes), so a change to the error format on either side now fails a test.

## 5. Honest limits of these numbers

- The 21 seeded faults are ones I chose, not a standard mutation tool. 21 of 21 is not a general score: the last 5 tests were written *to kill the 5 survivors*, so that jump is tuned to this list. The fairer figures are 10 to 16, which round 2 reached before looking at any survivor.
- Equivalent mutants are possible in a hand-picked list, but all 21 were eventually caught, so none was equivalent here.
- No real mutation tool (PIT) was run. No test was run against a real Postgres, real OpenAlex or the real LLM; those are mocked by design, so provider-specific behaviour is untested.
- The frontend suite was first run once in a sandbox copy before my own run (same 9 failures), and the backend was never run before my Mac; this is why the first Mac backend run is the true initial result.
- Backend branches are at 83.9%, not 100%, and I did not chase it.
