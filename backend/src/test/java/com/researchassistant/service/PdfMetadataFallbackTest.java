package com.researchassistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.researchassistant.service.PdfExtractionService.ExtractedPdf;
import com.researchassistant.service.PdfExtractionService.TextLine;

/** Round 1 (coverage gap): the fallbacks to the PDF's own document-info fields and the junk-title filter. */
class PdfMetadataFallbackTest {

    private static ExtractedPdf pdf(Integer infoYear, String infoTitle, String infoAuthor, TextLine... lines) {
        return new ExtractedPdf("", 1, infoTitle, infoAuthor, infoYear, List.of(lines));
    }

    @Test
    void usesDocumentInfoWhenThePageHasNoReadableText() {
        var meta = PdfMetadataExtractor.extract(pdf(2019, "Real Paper Title Here", "Jane Doe; John Smith and Ann Lee"));
        assertThat(meta.title()).isEqualTo("Real Paper Title Here");
        assertThat(meta.authors()).containsExactly("Jane Doe", "John Smith", "Ann Lee");
        assertThat(meta.year()).isEqualTo(2019);
    }

    @Test
    void junkInfoTitlesAreIgnoredAndReportedMissing() {
        for (String junk : new String[] { "Microsoft Word - draft.docx", "untitled", "paper.pdf", "abc" }) {
            var meta = PdfMetadataExtractor.extract(pdf(null, junk, null));
            assertThat(meta.title()).as("info title '%s'", junk).isNull();
            assertThat(meta.missing()).contains("title");
        }
    }

    @Test
    void anImplausibleInfoYearIsNotUsed() {
        assertThat(PdfMetadataExtractor.extract(pdf(1899, null, null)).year()).isNull();
        assertThat(PdfMetadataExtractor.extract(pdf(2999, null, null)).year()).isNull();
    }

    @Test
    void aVeryLongTitleIsCutAt300Characters() {
        String longTitle = ("Word ".repeat(100)).trim();
        var meta = PdfMetadataExtractor.extract(pdf(null, null, null, new TextLine(longTitle, 20)));
        assertThat(meta.title()).hasSize(300);
    }

    @Test
    void boilerplateHeaderIsSkippedWhenFindingTheTitle() {
        var meta = PdfMetadataExtractor.extract(pdf(null, null, null,
                new TextLine("arXiv:2101.00001v1 [cs.CR] 5 Jan 2021", 24),
                new TextLine("Real Title of the Paper", 20),
                new TextLine("Ann Lee", 12)));
        assertThat(meta.title()).isEqualTo("Real Title of the Paper");
    }
}
