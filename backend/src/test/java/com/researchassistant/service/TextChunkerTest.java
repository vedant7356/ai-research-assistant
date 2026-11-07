package com.researchassistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.researchassistant.service.TextChunker.Chunk;

class TextChunkerTest {

    @Test
    void emptyOrBlankTextGivesNoChunks() {
        assertThat(TextChunker.split("")).isEmpty();
        assertThat(TextChunker.split("  \n \n\t\n")).isEmpty();
    }

    @Test
    void shortTextBecomesOneChunkNumberedFromOne() {
        List<Chunk> chunks = TextChunker.split("First line.\nSecond line.");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).n()).isEqualTo(1);
        assertThat(chunks.get(0).text()).isEqualTo("First line. Second line.");
    }

    @Test
    void hyphenatedLineBreakIsRejoined() {
        List<Chunk> chunks = TextChunker.split("The exam-\nple works.");
        assertThat(chunks.get(0).text()).isEqualTo("The example works.");
    }

    @Test
    void longTextSplitsInOrderWithSequentialNumbers() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 40; i++) {
            sb.append("Line ").append(i).append(" talks about security and is padded to be fairly long so chunks fill up quickly here.\n");
        }
        List<Chunk> chunks = TextChunker.split(sb.toString());
        assertThat(chunks.size()).isGreaterThan(1);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).n()).isEqualTo(i + 1);
        }
        assertThat(chunks.get(0).text()).startsWith("Line 1 ");
        assertThat(chunks.get(chunks.size() - 1).text()).contains("Line 40 ");
    }

    @Test
    void chunksRespectTheMaximumSizeEvenWithoutSentenceEnds() {
        // MAX_CHARS is 1800: text with no sentence punctuation must still be cut at that size.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            sb.append("x".repeat(100)).append('\n');
        }
        for (Chunk c : TextChunker.split(sb.toString())) {
            assertThat(c.text().length()).isLessThanOrEqualTo(1800);
        }
    }

    @Test
    void topMatchesReturnsEverythingWhenThereAreFewChunks() {
        List<Chunk> chunks = List.of(new Chunk(1, "alpha"), new Chunk(2, "beta"));
        assertThat(TextChunker.topMatches(chunks, "anything", 5)).isEqualTo(chunks);
    }

    @Test
    void topMatchesPicksTheRelevantChunkAndKeepsDocumentOrder() {
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            chunks.add(new Chunk(i, i == 7 ? "the zebrafish zebrafish genome study" : "generic filler text number " + i));
        }
        List<Chunk> top = TextChunker.topMatches(chunks, "Where do zebrafish live?", 3);
        assertThat(top).hasSize(3);
        assertThat(top).extracting(Chunk::n).contains(7);
        assertThat(top).extracting(Chunk::n).isSorted();
    }

    @Test
    void topMatchesFallsBackToTheFirstChunksWhenNothingMatches() {
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 1; i <= 6; i++) chunks.add(new Chunk(i, "ordinary words here " + i));
        List<Chunk> top = TextChunker.topMatches(chunks, "quantum chromodynamics", 2);
        assertThat(top).extracting(Chunk::n).containsExactly(1, 2);
    }

    @Test
    void topMatchesIgnoresStopWordsInTheQuestion() {
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 1; i <= 6; i++) chunks.add(new Chunk(i, i == 5 ? "what the paper does about this" : "plain text " + i));
        // Every word in the question is a stop word, so no chunk should be preferred over the first ones.
        List<Chunk> top = TextChunker.topMatches(chunks, "what is this paper about", 2);
        assertThat(top).extracting(Chunk::n).containsExactly(1, 2);
    }
}
