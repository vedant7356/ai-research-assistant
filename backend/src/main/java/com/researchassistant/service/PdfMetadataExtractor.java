package com.researchassistant.service;

import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.researchassistant.service.PdfExtractionService.ExtractedPdf;
import com.researchassistant.service.PdfExtractionService.TextLine;

/**
 * Guesses title, authors, year and abstract from a PDF's first page. PDFs have no reliable structure,
 * so these are heuristics: they work on common paper layouts and fail on unusual ones. Callers must
 * treat every field as "maybe wrong" and let the user correct it.
 */
public final class PdfMetadataExtractor {

    public record Metadata(String title, List<String> authors, Integer year, String abstractText, List<String> missing) { }

    private static final Pattern YEAR = Pattern.compile("\\b(19[5-9]\\d|20[0-4]\\d)\\b");
    private static final Pattern ARXIV_ID = Pattern.compile("(?i)arxiv:\\s*\\S+");
    private static final Pattern ABSTRACT_START =
            Pattern.compile("(?im)^[ \\t]*abstract[ \\t]*[:.\\u2014\\u2013-]?[ \\t]*");
    private static final Pattern ABSTRACT_END = Pattern.compile(
            "(?im)^[ \\t]*(?:(?:1|I)[.\\s]*)?introduction\\b|^[ \\t]*(?:index terms|keywords?|ccs concepts)\\b");
    private static final Pattern BOILERPLATE = Pattern.compile(
            "(?i)^(arxiv|preprint|under review|proceedings|published as|accepted|copyright|\\u00a9|https?://|www\\.|\\d+$).*");
    private static final String[] AFFILIATION_WORDS = {
            "university", "universit", "institute", "department", "dept.", "laboratory", "school of", "college",
            "@", "google", "microsoft", "deepmind", "openai", "research", "corporation", "inc.", "ltd",
            "technology", "centre", "center", "faculty", "academy", "usa", "china", "india", "germany", "canada"
    };
    private static final int MAX_ABSTRACT_CHARS = 3000;

    private PdfMetadataExtractor() { }

    public static Metadata extract(ExtractedPdf pdf) {
        List<TextLine> lines = pdf.firstPageLines();

        int[] titleRange = findTitleRange(lines);
        String title = titleRange == null ? null : joinLines(lines, titleRange[0], titleRange[1]);
        if (title == null || title.length() < 4) title = saneInfoTitle(pdf.infoTitle());
        if (title == null && !lines.isEmpty()) title = clean(lines.get(0).text());

        List<String> authors = titleRange == null ? List.of() : findAuthors(lines, titleRange[1] + 1);
        if (authors.isEmpty()) authors = splitInfoAuthors(pdf.infoAuthor());

        Integer year = findYear(lines, pdf.infoYear());
        String abstractText = findAbstract(pdf.fullText());

        List<String> missing = new ArrayList<>();
        if (title == null) missing.add("title");
        if (authors.isEmpty()) missing.add("authors");
        if (year == null) missing.add("year");
        if (abstractText == null) missing.add("abstract");
        return new Metadata(title, authors, year, abstractText, missing);
    }

    // ---- title: the largest text on page 1 (skipping obvious headers) ----------------------------------

    /** Returns {firstLine, lastLine} (inclusive) of the title block, or null if page 1 has no usable text. */
    private static int[] findTitleRange(List<TextLine> lines) {
        int limit = Math.min(lines.size(), 40);
        float max = 0;
        for (int i = 0; i < limit; i++) {
            if (!isBoilerplate(lines.get(i).text())) max = Math.max(max, lines.get(i).fontSize());
        }
        if (max <= 0) return null;
        float threshold = max * 0.92f;
        int start = -1;
        for (int i = 0; i < limit; i++) {
            if (!isBoilerplate(lines.get(i).text()) && lines.get(i).fontSize() >= threshold) { start = i; break; }
        }
        if (start < 0) return null;
        int end = start;
        while (end + 1 < limit && end - start < 3 && lines.get(end + 1).fontSize() >= threshold
                && !isBoilerplate(lines.get(end + 1).text())) {
            end++;
        }
        return new int[] { start, end };
    }

    private static boolean isBoilerplate(String text) {
        String t = text.trim();
        return t.length() < 3 || BOILERPLATE.matcher(t).matches() || t.toLowerCase(Locale.ROOT).contains("arxiv:");
    }

    private static String joinLines(List<TextLine> lines, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i <= to; i++) {
            String t = lines.get(i).text().replace(PdfExtractionService.GAP, " ").trim();
            if (sb.length() > 0) {
                if (sb.charAt(sb.length() - 1) == '-') sb.setLength(sb.length() - 1);   // re-join hyphenated words
                else sb.append(' ');
            }
            sb.append(t);
        }
        String out = clean(sb.toString());
        return out.length() > 300 ? out.substring(0, 300) : out;
    }

    private static String saneInfoTitle(String t) {
        if (t == null) return null;
        String c = clean(t);
        String lower = c.toLowerCase(Locale.ROOT);
        if (c.length() < 5 || lower.startsWith("microsoft word") || lower.equals("untitled")
                || lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".tex")) {
            return null;
        }
        return c;
    }

    // ---- authors: name-like lines between the title and the abstract -------------------------------------

    private static List<String> findAuthors(List<TextLine> lines, int from) {
        Set<String> names = new LinkedHashSet<>();
        int to = Math.min(lines.size(), from + 14);
        for (int i = from; i < to; i++) {
            String line = lines.get(i).text().trim();
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("abstract") || lower.matches("^(1|i)?[.\\s]*introduction.*")) break;
            // A numbered section heading ("1 Problem Statement", "II. Background") means the author block is over.
            if (line.replace(PdfExtractionService.GAP, " ").matches("^(\\d{1,2}|[IVX]{1,4})[.\\s]+[A-Z][a-z]+.*")
                    && !line.contains("(")) break;
            if (isAffiliation(lower) || line.length() > 160 || line.length() < 3) continue;
            names.addAll(splitNames(line));
            if (names.size() >= 30) break;
        }
        return new ArrayList<>(names);
    }

    private static boolean isAffiliation(String lower) {
        for (String w : AFFILIATION_WORDS) if (lower.contains(w)) return true;
        return false;
    }

    /** "A B*, C D and E F" or "A B | C D (123)" -> [A B, C D, E F]. Wide gaps, commas, "and" and footnote marks separate names. */
    static List<String> splitNames(String line) {
        String s = line.replace(PdfExtractionService.GAP, ",")
                .replaceAll("\\([^)]*\\)", " ")   // student ids, "(corresponding author)"
                .replaceAll("[\\u2217*\\u2020\\u2021\\u00a7\\u00b6#]+", ",")
                .replace(" and ", ",").replace(" & ", ",").replace(";", ",");
        List<String> out = new ArrayList<>();
        for (String part : s.split(",")) {
            String p = part.replaceAll("[0-9]+", "").trim();
            if (p.isEmpty()) continue;
            String[] words = p.split("\\s+");
            if (!allNameWords(words)) return List.of();   // line is not a list of names
            if (words.length < 2 || words.length > 5) return List.of();
            out.add(String.join(" ", words));
        }
        return out;
    }

    private static boolean allNameWords(String[] words) {
        for (String w : words) {
            if (w.isEmpty()) return false;
            int cp = w.codePointAt(0);
            boolean particle = w.equals("van") || w.equals("de") || w.equals("von") || w.equals("der") || w.equals("bin") || w.equals("al");
            if (!particle && !Character.isUpperCase(cp)) return false;
            for (int i = 0; i < w.length(); i++) {
                char c = w.charAt(i);
                if (!Character.isLetter(c) && c != '.' && c != '-' && c != '\'' && c != '’') return false;
            }
        }
        return true;
    }

    private static List<String> splitInfoAuthors(String info) {
        if (info == null || info.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String p : info.split("[;,]| and ")) {
            String n = clean(p);
            if (n.length() >= 3 && n.length() <= 80) out.add(n);
        }
        return out;
    }

    // ---- year: earliest plausible year on page 1 ----------------------------------------------------------

    private static Integer findYear(List<TextLine> lines, Integer infoYear) {
        StringBuilder sb = new StringBuilder();
        for (TextLine l : lines) {
            sb.append(l.text()).append(' ');
            if (sb.length() > 3000) break;
        }
        // arXiv ids such as 2008.12345 look like years; remove them first.
        String text = ARXIV_ID.matcher(sb).replaceAll(" ");
        int latest = Year.now().getValue() + 1;
        Integer best = null;
        Matcher m = YEAR.matcher(text);
        while (m.find()) {
            int y = Integer.parseInt(m.group(1));
            if (y <= latest && (best == null || y < best)) best = y;
        }
        if (best != null) return best;
        return infoYear != null && infoYear >= 1950 && infoYear <= latest ? infoYear : null;
    }

    // ---- abstract: text after the "Abstract" heading, up to the introduction ------------------------------

    private static String findAbstract(String fullText) {
        if (fullText == null) return null;
        String head = fullText.length() > 15000 ? fullText.substring(0, 15000) : fullText;
        Matcher start = ABSTRACT_START.matcher(head);
        if (!start.find()) return null;
        String rest = head.substring(start.end());
        Matcher end = ABSTRACT_END.matcher(rest);
        int stop = end.find() ? end.start() : Math.min(rest.length(), MAX_ABSTRACT_CHARS);
        String text = rest.substring(0, Math.min(stop, rest.length())).replace("-\n", "").replace('\n', ' ');
        text = clean(text);
        if (text.length() > MAX_ABSTRACT_CHARS) {
            int cut = text.lastIndexOf(". ", MAX_ABSTRACT_CHARS);
            text = text.substring(0, cut > 200 ? cut + 1 : MAX_ABSTRACT_CHARS);
        }
        return text.length() < 40 ? null : text;
    }

    private static String clean(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }
}
