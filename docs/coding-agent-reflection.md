# Coding Agent Reflection Log

Agent: Claude (Claude Code harness in the Claude desktop app), single agent for the whole build.
Only real events are recorded here. Add your own manual edits where marked.

## Entry 1 — Environment setup (CP1)
1. Goal: install tooling, scaffold project.
2. Agent: Claude.
3. Prompt: "approved" + `brew --version` output.
4. Generated: install commands; later a Vite scaffold and docs.
5. Worked? Yes: Maven, Postgres, backend start, /api/health and frontend all verified from the user's screenshots.
6. Problems:
   - Agent first assumed it could edit files; no project folder was connected, so it had to ask the user to add one.
   - After the folder was added, the agent's shell turned out to be a Linux VM, not the Mac: it cannot see Homebrew installs or the Mac's local Postgres, so install/run checks must be run by the user.
   - Spring Initializr (start.spring.io) is blocked from the agent's shell; backend generation moved to a command the user runs.
   - The agent's `psql` command opened a pager that swallowed the following `mvn -v` (visible in user's screenshot) — fix: `psql -P pager=off`.
   - Agent could not delete scaffold leftovers (permission).
7. Debugging: checked tool availability from the shell before assuming.
8. Manual by user: [fill in]
9. Outcome: [fill in at end of CP1]
10. Learned: [fill in]

## Screenshot evidence checklist
- [x] psql pager / `mvn -v` swallowed (user screenshot of terminal, CP1)
- [ ] Agent folder-access / VM limitation exchange (chat screenshot)
- [ ] Later: first real bug in generated code and its fix

## Entry 2 — UI design phase (before CP2)
1. Goal: get a UI design before building the frontend.
2. Tool: Claude Design (separate Claude product, canvas artifact). Prompt used: [fill in].
3. Generated: 8 boards (clickable prototype, 2 mobile screens, design spec, flows, frontend architecture, REST contract, states matrix).
4. Problems noticed by the build agent: the design's API contract and stack (Tailwind, React Router, TanStack Query, mock adapter, two-step upload confirm, persisted conversations, Settings page, Dashboard) go beyond the assignment and conflict with the approved plan (e.g. GET /papers/{id} for unsaved search results would require the backend to store search results). Time risk with ~16 h left.
5. Resolution: [fill in after user decision]
6. Learned: [fill in]

## Entry 3 — Choosing the search API (CP2)
1. Goal: pick a working public paper-search API.
2. Prompt: user's project brief said to investigate Semantic Scholar first and verify before committing.
3. Agent actions: read Semantic Scholar and OpenAlex docs; sandbox could not reach either API (network allowlist), so the agent asked the user to run curl tests on the Mac.
4. Result: Semantic Scholar returned 429 on all 3 tries; OpenAlex returned 200.
5. Problem: the agent's initial plan (Semantic Scholar first) did not survive a live test. Docs alone ("no key needed") did not predict the throttling.
6. Fix: switched to OpenAlex; documented key option.
7. Learned: [fill in]

## Entry 4 — Library save returned HTTP 500 (CP3)
1. Goal: POST /api/library to save a paper; verify with curl.
2. Agent generated: save endpoint reusing the search DTO (PaperDto, which included a search-only field inLibrary) plus a catch-all @ExceptionHandler(Exception.class).
3. What happened: user's curl saves (without inLibrary) both returned HTTP 500, while the browser save worked (the browser sends every field).
4. Diagnosis (done by asking for evidence, not guessing): the agent first fixed the handler flaw it could see in its own code (catch-all turned client errors into 500). After the restart the same request returned 400 VALIDATION_FAILED, and adding "inLibrary":false made it 201. So the request body was rejected for the missing field; the 500 was the handler hiding a 400.
5. Fix: dedicated SavePaperRequest record (no inLibrary, openAccess as Boolean), frontend sends only stored fields, handler keeps Spring's 4xx statuses.
6. Lesson: a shared DTO leaked a response-only field into a request; a catch-all error handler hides root causes; test endpoints with minimal curl payloads, not only the UI. Exact Jackson setting behind the rejection was not inspected. [add your own notes]

## Entry 5 — PDF metadata wrong on real papers (CP4)
1. Goal: extract title/authors/year/abstract from uploaded PDFs with PDFBox.
2. Agent generated: first-page heuristics (largest font = title, name-like lines = authors). Passed on a clean single-column paper.
3. What happened: on the user's own proposals, authors came back as "Unknown", then "+2" extra names, then "Problem Statement" as a fifth author.
4. Diagnosis: the agent read the PDFs with pdftotext/bbox, and later ran PDFBox itself in the sandbox (an old 1.8 jar) to print the real line stream. That showed margin line numbers arriving as separate lines before the real text, and side-by-side authors arriving as one line with no separator. The agent's first fix (strip numbers inside a line) was wrong because it assumed the wrong structure; it only worked after looking at actual extractor output.
5. Fixes: wide x-gap becomes a separator; digit-only lines dropped; ids in parentheses stripped; numbered section heading ends the author block; word-pairing heuristic removed.
6. Lesson: heuristic parsers must be tested against the user's real files, not clean samples; a fix made without looking at the raw data was a guess. Remaining limits: no OCR, odd layouts still misread, review form lets the user correct fields. [add your own notes and screenshots]
