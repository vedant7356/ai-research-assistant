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

## Checkpoints

- [x] CP0 setup and baseline (frontend 0 tests; backend `contextLoads` passes on H2)
- [ ] CP1 initial suite generated
- [ ] CP2 initial evaluation
- [ ] CP3 improvement round 1
- [ ] CP4 improvement round 2
- [ ] CP5 final evaluation
- [ ] CP6 README + clean-clone test + push
- [ ] CP7 report + submit
