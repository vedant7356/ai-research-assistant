# Requirements Checklist

Status: NOT STARTED / DONE (implemented) / VERIFIED (tested, evidence in testing-log.md)

| # | Requirement | Component | CP | Status |
|---|---|---|---|---|
| F1 | Search papers (title, authors, year, abstract, link) | SearchService + React | 2 | PASS |
| F2 | Save papers; browse; survives refresh and backend restart | Postgres/JPA | 3 | PASS |
| F3 | Upload PDF; extract title/authors/year/abstract (PDFBox) | UploadService | 4 | PASS |
| F4 | LLM summary from paper content | LlmClient + SummaryService | 5 | PASS |
| F5 | Q&A on a selected paper | chunk retrieval + LLM | 6 | PASS |
| S1 | GitHub repo: source, README, features, configs, no secrets | repo | 8 | NOT STARTED |
| S2 | Demo video (search, save, upload, summarize, one question) | - | 8 | NOT STARTED |
| S3 | Reflection report (5 questions, genuine screenshots) | docs/ | all | IN PROGRESS |

Not mandatory: deployment (not in submission list), automated tests (deferred to HW2).
