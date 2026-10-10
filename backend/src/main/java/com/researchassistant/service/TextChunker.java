package com.researchassistant.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits paper text into excerpts and picks the ones most relevant to a question (keyword scoring, no embeddings). */
public final class TextChunker {

    /** One numbered excerpt of the paper. n starts at 1 and follows document order. */
    public record Chunk(int n, String text) { }

    private static final int TARGET_CHARS = 1200;
    private static final int MAX_CHARS = 1800;
    private static final Pattern WORD = Pattern.compile("[a-z0-9][a-z0-9-]{2,}");
    private static final Set<String> STOP = Set.of(
            "the", "and", "for", "are", "was", "were", "what", "which", "who", "whom", "how", "why", "when", "where",
            "this", "that", "these", "those", "with", "from", "into", "does", "did", "has", "have", "had", "can",
            "could", "would", "should", "about", "paper", "article", "study", "authors", "author", "their", "they",
            "them", "its", "his", "her", "not", "but", "you", "your", "tell", "explain", "describe", "any", "all");

    private TextChunker() { }

    public static List<Chunk> split(String text) {
        List<Chunk> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String raw : text.split("\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (cur.length() > 0) {
                char last = cur.charAt(cur.length() - 1);
                if (last == '-') cur.setLength(cur.length() - 1); else cur.append(' ');
            }
            cur.append(line);
            boolean sentenceEnd = line.endsWith(".") || line.endsWith("?") || line.endsWith(":");
            if ((cur.length() >= TARGET_CHARS && sentenceEnd) || cur.length() >= MAX_CHARS) {
                out.add(new Chunk(out.size() + 1, cur.toString()));
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) out.add(new Chunk(out.size() + 1, cur.toString()));
        return out;
    }

    /** The k chunks that best match the question, returned in document order. Falls back to the first chunks if nothing matches. */
    public static List<Chunk> topMatches(List<Chunk> chunks, String question, int k) {
        if (chunks.size() <= k) return chunks;
        Set<String> terms = new HashSet<>(words(question));
        Map<String, Integer> docFreq = new HashMap<>();
        List<Map<String, Integer>> tfs = new ArrayList<>();
        for (Chunk c : chunks) {
            Map<String, Integer> tf = new HashMap<>();
            for (String w : words(c.text())) tf.merge(w, 1, Integer::sum);
            tfs.add(tf);
            for (String t : terms) if (tf.containsKey(t)) docFreq.merge(t, 1, Integer::sum);
        }
        double[] score = new double[chunks.size()];
        for (int i = 0; i < chunks.size(); i++) {
            for (String t : terms) {
                Integer f = tfs.get(i).get(t);
                if (f == null) continue;
                double idf = Math.log(1.0 + (double) chunks.size() / docFreq.get(t));
                score[i] += (1.0 + Math.log(f)) * idf;
            }
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) order.add(i);
        order.sort((a, b) -> Double.compare(score[b], score[a]) != 0 ? Double.compare(score[b], score[a]) : Integer.compare(a, b));
        List<Integer> picked = new ArrayList<>(order.subList(0, k));
        picked.sort(Integer::compare);
        List<Chunk> out = new ArrayList<>();
        for (int i : picked) out.add(chunks.get(i));
        return out;
    }

    private static List<String> words(String s) {
        List<String> out = new ArrayList<>();
        Matcher m = WORD.matcher(s.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String w = m.group();
            if (!STOP.contains(w)) out.add(w);
        }
        return out;
    }
}
