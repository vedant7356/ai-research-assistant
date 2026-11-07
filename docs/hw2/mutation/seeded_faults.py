#!/usr/bin/env python3
"""
Seeded-fault check for HW2: plant one small deliberate bug at a time in a throw-away copy of the backend,
run the test suite, and record whether any test that passed before now fails ("caught") or not ("missed").

Usage (from the repo root, with all work committed):
    python3 docs/hw2/mutation/seeded_faults.py

It makes two git worktrees under /tmp (the commit of the initial suite and the current HEAD), never touches your
working folder, and writes docs/hw2/mutation/results.md. Takes roughly 10 minutes.
"""
import glob, os, re, shutil, subprocess, sys, time
import xml.etree.ElementTree as ET

INITIAL_COMMIT = "5793274"
SRC = "backend/src/main/java/com/researchassistant/"

# id, file, exact text to replace (must occur once), replacement, what it models
MUTANTS = [
 ("M01", "service/QaService.java", "question.length() > MAX_QUESTION_CHARS", "question.length() >= MAX_QUESTION_CHARS", "question length limit off by one"),
 ("M02", "service/QaService.java", "text.length() <= WHOLE_PAPER_MAX_CHARS", "text.length() < WHOLE_PAPER_MAX_CHARS", "whole-paper threshold off by one"),
 ("M03", "service/QaService.java", "if (cited.contains(c.n())) {", "if (!cited.contains(c.n())) {", "shows the uncited excerpts instead of the cited ones"),
 ("M04", "service/QaService.java", "c.text().length() > 500 ? c.text().substring(0, 500)", "c.text().length() > 900 ? c.text().substring(0, 900)", "source excerpt cut at the wrong length"),
 ("M05", "service/LibraryService.java", "String.join(\" \", p.authorList()).toLowerCase(Locale.ROOT).contains(needle)", "false", "library search ignores authors"),
 ("M06", "service/LibraryService.java", "Comparator.<Integer>reverseOrder()", "Comparator.<Integer>naturalOrder()", "year sort oldest first instead of newest first"),
 ("M07", "service/LibraryService.java", "return new SaveResult(LibraryItemDto.from(existing.get()), false);", "return new SaveResult(LibraryItemDto.from(existing.get()), true);", "saving a duplicate reported as newly created"),
 ("M08", "service/LibraryService.java", ".filter(a -> !a.isEmpty())", ".filter(a -> true)", "blank authors kept on edit"),
 ("M09", "service/UploadService.java", "MIN_TEXT_CHARS = 100", "MIN_TEXT_CHARS = 10", "scanned PDFs with a little text accepted"),
 ("M10", "service/UploadService.java", ".trim().length() < MIN_TEXT_CHARS", ".trim().length() <= MIN_TEXT_CHARS", "empty-PDF limit off by one"),
 ("M11", "service/SummaryService.java", "if (text.length() > MAX_SOURCE_CHARS) {", "if (text.length() >= MAX_SOURCE_CHARS) {", "summary truncation limit off by one"),
 ("M12", "service/SummaryService.java", "if (!refresh && p.getSummary() != null", "if (refresh && p.getSummary() != null", "summary cache used only when refreshing"),
 ("M13", "service/PaperSearchService.java", "Math.min(Math.max(limit, 1), 25)", "Math.min(Math.max(limit, 1), 50)", "page size cap too high"),
 ("M14", "service/PaperSearchService.java", "int safePage = Math.max(page, 1);", "int safePage = Math.max(page, 0);", "page 0 allowed"),
 ("M15", "service/PdfMetadataExtractor.java", "y < best", "y > best", "year picks the latest year on the page, not the earliest"),
 ("M16", "service/PdfMetadataExtractor.java", "infoYear >= 1950", "infoYear >= 1850", "implausible PDF-info year accepted"),
 ("M17", "service/PdfMetadataExtractor.java", "out.length() > 300 ? out.substring(0, 300)", "out.length() > 3000 ? out.substring(0, 3000)", "title length cap removed"),
 ("M18", "exception/GlobalExceptionHandler.java", "ResponseEntity.status(413)", "ResponseEntity.status(400)", "oversize upload reported as 400"),
 ("M19", "service/PaperContentService.java", "MAX_PDF_BYTES = 25 * 1024 * 1024", "MAX_PDF_BYTES = 1024", "PDF download size cap far too small"),
 ("M20", "service/TextChunker.java", "MAX_CHARS = 1800", "MAX_CHARS = 3600", "excerpt maximum doubled"),
 ("M21", "service/TextChunker.java", "TARGET_CHARS = 1200", "TARGET_CHARS = 2400", "excerpt target doubled"),
]

def sh(cmd, cwd=None, check=True):
    return subprocess.run(cmd, cwd=cwd, shell=isinstance(cmd, str), capture_output=True, text=True, check=check)

def failures(backend):
    out = set()
    for f in glob.glob(os.path.join(backend, "target/surefire-reports/TEST-*.xml")):
        for tc in ET.parse(f).getroot().iter("testcase"):
            if tc.find("failure") is not None or tc.find("error") is not None:
                out.add(tc.get("classname") + "." + tc.get("name"))
    return out

def run_tests(backend):
    shutil.rmtree(os.path.join(backend, "target/surefire-reports"), ignore_errors=True)
    r = sh(["./mvnw", "-q", "test", "-Dmaven.test.failure.ignore=true", "-Djacoco.skip=true"], cwd=backend, check=False)
    ran = glob.glob(os.path.join(backend, "target/surefire-reports/TEST-*.xml"))
    return (failures(backend) if ran else None), r.stdout + r.stderr

def main():
    repo = sh("git rev-parse --show-toplevel").stdout.strip()
    trees = {"initial": "/tmp/hw2-initial", "final": "/tmp/hw2-final"}
    refs = {"initial": INITIAL_COMMIT, "final": "HEAD"}
    for k, d in trees.items():
        if os.path.exists(d):
            sh(["git", "worktree", "remove", "--force", d], cwd=repo, check=False)
            shutil.rmtree(d, ignore_errors=True)
        sh(["git", "worktree", "add", "--detach", d, refs[k]], cwd=repo)
    results = {k: {} for k in trees}
    try:
        for k, d in trees.items():
            backend = os.path.join(d, "backend")
            base, log = run_tests(backend)
            if base is None:
                sys.exit("baseline did not run for %s:\n%s" % (k, log[-2000:]))
            print("[%s] baseline: %d failing tests already" % (k, len(base)), flush=True)
            for mid, rel, old, new, what in MUTANTS:
                path = os.path.join(d, SRC + rel)
                src = open(path, encoding="utf-8").read()
                if src.count(old) != 1:
                    results[k][mid] = "n/a"
                    print("[%s] %s: pattern found %d times, skipped" % (k, mid, src.count(old)), flush=True)
                    continue
                open(path, "w", encoding="utf-8").write(src.replace(old, new))
                try:
                    t0 = time.time()
                    fails, log = run_tests(backend)
                    if fails is None:
                        verdict = "invalid"
                    else:
                        newly = fails - base
                        verdict = "caught" if newly else "missed"
                        results[k][mid + "_by"] = sorted(newly)[:3]
                    results[k][mid] = verdict
                    print("[%s] %s %s (%.0fs)" % (k, mid, verdict, time.time() - t0), flush=True)
                finally:
                    open(path, "w", encoding="utf-8").write(src)
    finally:
        for d in trees.values():
            sh(["git", "worktree", "remove", "--force", d], cwd=repo, check=False)

    rows, tot = [], {"initial": [0, 0], "final": [0, 0]}
    for mid, rel, old, new, what in MUTANTS:
        a, b = results["initial"].get(mid, "n/a"), results["final"].get(mid, "n/a")
        rows.append("| %s | `%s` | %s | %s | %s |" % (mid, rel.split("/")[-1], what, a, b))
        for k, v in (("initial", a), ("final", b)):
            if v in ("caught", "missed"):
                tot[k][1] += 1
                tot[k][0] += v == "caught"
    md = ["# Seeded-fault results", "",
          "One deliberate bug at a time in a throw-away copy of the backend; *caught* = a test that passed before now fails.", "",
          "| id | file | seeded fault | initial suite | final suite |", "|---|---|---|---|---|"] + rows + ["",
          "**Initial suite:** %d of %d caught. **Final suite:** %d of %d caught." % (tot["initial"][0], tot["initial"][1], tot["final"][0], tot["final"][1]), ""]
    out = os.path.join(repo, "docs/hw2/mutation/results.md")
    open(out, "w").write("\n".join(md))
    print("\n".join(md))

if __name__ == "__main__":
    main()
