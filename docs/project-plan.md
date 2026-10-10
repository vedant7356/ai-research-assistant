# Project Plan — AI Research Assistant (HW1)

Deadline: Oct 10, 2026, 11:59 PM. Stack: React + TS (Vite) → Spring Boot (Java 21, Maven) → PostgreSQL; PDFBox; paper-search API; Purdue GenAI Studio LLM.

Approved design decisions (Checkpoint 0):
1. Saved search papers: backend tries the open-access PDF to get full text; falls back to abstract-only (labelled).
2. One `papers` table (source = SEARCH | UPLOAD).
3. Store extracted text only, not PDF bytes.

| CP | Scope | Status |
|---|---|---|
| 0 | Requirements + architecture | PASS (approved) |
| 1 | Environment + scaffolding | PASS |
| 2 | Search | PASS |
| 3 | Library persistence | PASS |
| 4 | PDF upload | IN PROGRESS |
| 5 | Summarization | - |
| 6 | Q&A | - |
| 7 | Integration testing | - |
| 8 | GitHub, README, demo, report | - |

## Design handoff (Claude Design canvas, received 2026-10-10 ~07:13) — APPROVED by user ("adopt the look, simplified structure")
The design canvas contains visual tokens, flows, a states matrix, a frontend architecture and a REST contract. It is larger than the assignment needs and differs from the approved plan.
Proposed: adopt the visual design (colors, fonts, layout, states); keep our simpler backend API; skip Tailwind/TanStack Query/mock adapter/Settings/Dashboard/persisted chat history/two-step upload confirm.

## Search API decision (CP2, 2026-10-10 07:27)
Tested from the user's Mac: Semantic Scholar unauthenticated search returned HTTP 429 on 3/3 tries (shared pool throttled); OpenAlex returned HTTP 200 without a key.
Decision: use OpenAlex (abstracts come as an inverted index and are reconstructed server-side). Optional free API key (OPENALEX_API_KEY) raises the daily budget; without a key the budget is small (~$0.10/day per OpenAlex docs).
