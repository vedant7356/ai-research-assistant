# Architecture

React + TS (Vite, :5173) → /api proxy → Spring Boot (:8080) → PostgreSQL (:5432)
                                                           → paper-search API
                                                           → LLM API (key server-side only)

Backend packages (com.researchassistant): controller, service, repository, entity, dto, config.
Config via environment variables (see .env.example). The Vite dev server proxies /api to the backend, so no CORS setup is needed in dev.

Data model: single `papers` table — id, source (SAVED|UPLOAD), external_id (unique, nullable), title, authors, year, abstract, url, pdf_url, open_access, full_text, original_filename, content_hash (unique, nullable), summary, summary_basis, created_at.

LLM flow: LlmClient (OpenAI-style POST {base}/chat/completions, key from env) <- SummaryService (stored full text, else open-access PDF fetched once by PaperContentService, else abstract; capped at 60k chars; cached in papers.summary) and QaService (TextChunker splits into ~1200-char excerpts; short papers go whole, longer ones get the 8 best keyword matches; answer must cite [n]; cited excerpts are returned to the UI).
