package com.researchassistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.researchassistant.service.PdfExtractionService.ExtractedPdf;
import com.researchassistant.service.PdfExtractionService.TextLine;

class PdfMetadataExtractorTest {

    private static TextLine l(String text, float size) {
        return new TextLine(text, size);
    }

    private static ExtractedPdf pdf(String fullText, Integer infoYear, String infoTitle, String infoAuthor, TextLine... lines) {
        return new ExtractedPdf(fullText, 1, infoTitle, infoAuthor, infoYear, List.of(lines));
    }

    private static final String ABSTRACT_TEXT = "Abstract\nWe study how mobile apps store data and show that many of them are\n"
            + "insecure by default.\n1 Introduction\nBody text follows here.";

    @Test
    void extractsTitleAuthorsYearAndAbstractFromATypicalFirstPage() {
        var meta = PdfMetadataExtractor.extract(pdf(ABSTRACT_TEXT, null, null, null,
                l("Attention Mechanisms for Mobile Security", 20),
                l("Jane Doe, John Smith", 12),
                l("Purdue University, USA", 10),
                l("October 2021", 10)));
        assertThat(meta.title()).isEqualTo("Attention Mechanisms for Mobile Security");
        assertThat(meta.authors()).containsExactly("Jane Doe", "John Smith");
        assertThat(meta.year()).isEqualTo(2021);
        assertThat(meta.abstractText()).startsWith("We study how mobile apps").endsWith("insecure by default.");
        assertThat(meta.missing()).isEmpty();
    }

    @Test
    void titleSpanningTwoLinesIsJoinedAndHyphenatedWordIsRepaired() {
        var meta = PdfMetadataExtractor.extract(pdf("", null, null, null,
                l("Federated Learn-", 20), l("ing at the Edge", 20), l("Ann Lee", 12)));
        assertThat(meta.title()).isEqualTo("Federated Learning at the Edge");
        assertThat(meta.authors()).containsExactly("Ann Lee");
    }

    @Test
    void arxivStampIsNotUsedAsTitleAndItsIdIsNotMistakenForAYear() {
        var meta = PdfMetadataExtractor.extract(pdf("", null, null, null,
                l("arXiv:2008.12345v1 [cs.LG] 3 Aug 2020", 9),
                l("Real Title Of Paper", 18),
                l("Sam Poe", 11)));
        assertThat(meta.title()).isEqualTo("Real Title Of Paper");
        assertThat(meta.year()).isEqualTo(2020);
    }

    @Test
    void numberedSectionHeadingEndsTheAuthorBlock() {
        var meta = PdfMetadataExtractor.extract(pdf("", null, null, null,
                l("Some Paper Title", 20),
                l("Alice Brown", 12),
                l("1 Problem Statement", 12),
                l("Bob Green", 12)));
        assertThat(meta.authors()).containsExactly("Alice Brown");
    }

    @Test
    void futureYearsAreIgnoredAndTheDocumentInfoYearIsUsedInstead() {
        var meta = PdfMetadataExtractor.extract(pdf("", 2018, null, null,
                l("Title Words Here", 20), l("Ann Lee", 12), l("Copyright 2045", 9)));
        assertThat(meta.year()).isEqualTo(2018);
    }

    @Test
    void usesTheDocumentInfoWhenThePageHasNoText() {
        var meta = PdfMetadataExtractor.extract(pdf("", 2019, "Real Title Here", "Ann Lee; Bo Chan"));
        assertThat(meta.title()).isEqualTo("Real Title Here");
        assertThat(meta.authors()).containsExactly("Ann Lee", "Bo Chan");
        assertThat(meta.year()).isEqualTo(2019);
    }

    @Test
    void uselessInfoTitleIsRejected() {
        var meta = PdfMetadataExtractor.extract(pdf("", null, "Microsoft Word - draft.docx", null));
        assertThat(meta.title()).isNull();
        assertThat(meta.missing()).contains("title");
    }

    @Test
    void reportsEveryFieldAsMissingForAnEmptyPage() {
        var meta = PdfMetadataExtractor.extract(pdf("", null, null, null));
        assertThat(meta.title()).isNull();
        assertThat(meta.authors()).isEmpty();
        assertThat(meta.year()).isNull();
        assertThat(meta.abstractText()).isNull();
        assertThat(meta.missing()).containsExactly("title", "authors", "year", "abstract");
    }

    @Test
    void abstractStopsAtKeywordsAndRejoinsHyphenatedLineBreaks() {
        String text = "Title\nAbstract: Mobile secu-\nrity matters because phones hold personal data about everyone.\n"
                + "Keywords: security, mobile\nMore text.";
        var meta = PdfMetadataExtractor.extract(pdf(text, null, null, null, l("Some Title Here", 20)));
        assertThat(meta.abstractText()).isEqualTo("Mobile security matters because phones hold personal data about everyone.");
    }

    @Test
    void tooShortAbstractCountsAsMissing() {
        var meta = PdfMetadataExtractor.extract(pdf("Abstract\nToo short.\n1 Introduction\nText", null, null, null,
                l("Some Title Here", 20)));
        assertThat(meta.abstractText()).isNull();
        assertThat(meta.missing()).contains("abstract");
    }

    @Test
    void abstractWithoutAnEndMarkerIsCappedAtThreeThousandCharacters() {
        String text = "Abstract\n" + "This sentence repeats to make a very long abstract. ".repeat(200);
        var meta = PdfMetadataExtractor.extract(pdf(text, null, null, null, l("Some Title Here", 20)));
        assertThat(meta.abstractText()).isNotNull();
        assertThat(meta.abstractText().length()).isLessThanOrEqualTo(3000);
    }

    @Test
    void splitNamesHandlesCommonSeparatorsAndMarks() {
        assertThat(PdfMetadataExtractor.splitNames("Alice Brown and Bob Green")).containsExactly("Alice Brown", "Bob Green");
        assertThat(PdfMetadataExtractor.splitNames("Alice Brown*, Bob Green†")).containsExactly("Alice Brown", "Bob Green");
        assertThat(PdfMetadataExtractor.splitNames("Alice Brown (12345), Bob Green (67890)")).containsExactly("Alice Brown", "Bob Green");
        assertThat(PdfMetadataExtractor.splitNames("Alice Brown" + PdfExtractionService.GAP + "Bob Green")).containsExactly("Alice Brown", "Bob Green");
        assertThat(PdfMetadataExtractor.splitNames("Ludwig van Beethoven")).containsExactly("Ludwig van Beethoven");
    }

    @Test
    void splitNamesRejectsLinesThatAreNotNames() {
        assertThat(PdfMetadataExtractor.splitNames("Deep learning for security")).isEmpty();
        assertThat(PdfMetadataExtractor.splitNames("Madonna")).isEmpty();
    }
}
