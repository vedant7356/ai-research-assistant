# Testing Log

| Date | CP | Test | Result | Notes |
|---|---|---|---|---|
| 2026-10-10 | 1 | psql connects, DB research_assistant exists | PASS | PostgreSQL 17.11, from user screenshot |
| 2026-10-10 | 1 | `mvn -v` | PASS | Maven 3.10.0, Java 21.0.10 (user screenshot) |
| 2026-10-10 | 1 | Backend starts, connects to Postgres | PASS | Spring Boot 4.1.1, Hibernate shows PostgreSQL 17.11 (user screenshot) |
| 2026-10-10 | 1 | GET /api/health | PASS | {"status":"UP","database":"UP",...} (user screenshot) |
| 2026-10-10 | 1 | Frontend dev server | PASS | Vite ready on :5173; page rendered; showed expected 502 before backend was up |
| 2026-10-10 | 2 | Semantic Scholar search x3 (no key) | FAIL (429) | "Too Many Requests", from user screenshot -> switched provider |
| 2026-10-10 | 2 | OpenAlex works search (no key) | PASS (HTTP 200) | from user screenshot |
| 2026-10-10 | 2 | GET /api/papers/search?q=attention is all you need&limit=2 | PASS | real results incl. abstracts, from user screenshot; first hit shows year 2025 (source data) |
| 2026-10-10 | 2 | GET /api/papers/search?q= | PASS | 400 EMPTY_QUERY, from user screenshot |
| 2026-10-10 | 2 | Frontend tsc + vite build (agent, scratch copy) | PASS | not a browser test |
| 2026-10-10 | 2 | Discover page in browser (6-step manual test) | PASS (user-reported) | screenshots pending |
| 2026-10-10 | 3 | POST /api/library without inLibrary field | FAIL (500, later 400) | user curl; cause: shared DTO + catch-all handler; fix in progress |
| 2026-10-10 | 3 | POST /api/library with inLibrary:false | PASS (201) | user curl, row id 3 created |
| 2026-10-10 | 3 | Library page shows saved paper | PASS | user screenshot ("DISCOVER", saved from search) |
| 2026-10-10 | 3 | Duplicate save x2 (minimal payload) | PASS | HTTP 200, 200 (user screenshot); also proves row survived backend restart |
| 2026-10-10 | 3 | Remove paper from library | PASS | list went from 2 to 1 paper (user screenshot) |
| 2026-10-10 | 4 | Frontend tsc + vite build (agent, scratch copy) | PASS | not a browser test |
| 2026-10-10 | 4 | Upload fake.pdf / empty.pdf | PASS | 400/422 error envelopes (user screenshot) |
| 2026-10-10 | 4 | Select an image file in upload picker | PASS | not accepted (user-reported) |
| 2026-10-10 | 4 | Real PDF: How to Read a Paper | PASS | title, author, year correct (user screenshot) |
| 2026-10-10 | 4 | Real PDF: review-copy proposal (line numbers, student IDs) | FAIL then PASS | no authors; fixed by dropping digit-only lines (user re-test) |
| 2026-10-10 | 4 | Real PDF: 4-author column layout | FAIL then PASS | "+2" extra names, then "Problem Statement" captured; both fixed (user re-test) |
| 2026-10-10 | 4 | PDF without Abstract heading | PASS (expected) | abstract empty with prompt to paste |
| 2026-10-10 | 5 | curl chat/completions with gpt-oss:120b | PASS | real reply, user screenshot (key not shown) |
| 2026-10-10 | 5 | Summarize uploaded PDF (crypto proposal) | PASS | full-text basis, content matches paper (user screenshot) |
| 2026-10-10 | 5 | Summarize saved search paper (no PDF available) | PASS | labeled abstract only; no invented limitations (user screenshot) |
| 2026-10-10 | 5 | Regenerate + persistence badge | PASS | text changed, badge persisted (user screenshot) |
| 2026-10-10 | 5 | Wrong key / rate limit messages | NOT TESTED | code path only |
| 2026-10-10 | 6 | Ask in-paper question (crypto) | PASS (answer) / FAIL (passages) | correct answer; 【4】 citation not parsed, no passages shown; fixed, retest pending |
| 2026-10-10 | 6 | Ask question not in paper (ImageNet accuracy) | PASS | "The paper text I have does not say." (user screenshot) |
| 2026-10-10 | 6 | Prompt injection in question (capital of France) | PASS | refused, no outside knowledge (user screenshot) |
| 2026-10-10 | 6 | Ask on second paper (Keshav) | PASS | grounded, cited, 6 passages (user screenshot) |
| 2026-10-10 | 6 | Ask on abstract-only paper | PASS | labeled "answered from the abstract only" (user screenshot) |
| 2026-10-10 | 6 | Retest after citation fixes (crypto paper) | PASS | clean [4][5], Passages used (2) show supporting text (user screenshot) |
| 2026-10-10 | 7 | Check 1: cold start (both servers, status panel) | PASS | user-reported |
| 2026-10-10 | 7 | Check 2: full flow (search, save two, upload, summarize, ask) | PASS | user-reported |
| 2026-10-10 | 7 | Check 3: backend restart + refresh; papers, summaries persist; new question works | PASS | user-reported |
| 2026-10-10 | 7 | Check 4: saved open-access paper ("ChatGPT Alternative Solutions: LLM Survey") then Summarize | PASS | badge became Full text (PDF fetched from pdfUrl and cached); summary quotes body-level figures (user screenshots) |
| 2026-10-10 | 7 | Check 5: backend stopped -> Discover shows error + Retry; recovers after restart | PASS | user-reported |
| 2026-10-10 | 7 | Check 6: README matches setup and features | PASS | user-reported (read through; setup steps were not re-run from a clean machine) |
