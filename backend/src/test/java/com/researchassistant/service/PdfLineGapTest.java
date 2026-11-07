package com.researchassistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.researchassistant.service.PdfExtractionService.ExtractedPdf;
import com.researchassistant.support.TestPdfs;

/** Round 1 (coverage gap): wide horizontal gaps become the GAP marker, which the author heuristics rely on. */
class PdfLineGapTest {

    private final PdfExtractionService service = new PdfExtractionService();

    @Test
    void twoFarApartRunsOnOneBaselineAreJoinedWithTheGapMarker() {
        ExtractedPdf pdf = service.extract(TestPdfs.twoRunsOnOneLine("Jane Doe", "John Smith", 12));
        assertThat(pdf.firstPageLines()).hasSize(1);
        assertThat(pdf.firstPageLines().get(0).text()).isEqualTo("Jane Doe" + PdfExtractionService.GAP + "John Smith");
    }

    @Test
    void aMarginLineNumberFarFromTheTextIsDropped() {
        ExtractedPdf pdf = service.extract(TestPdfs.twoRunsOnOneLine("Real sentence on the page", "42", 12));
        assertThat(pdf.firstPageLines().get(0).text()).isEqualTo("Real sentence on the page");
    }

    @Test
    void dropMarginNumbersKeepsLongNumbersAndLinesWithoutAGap() {
        assertThat(PdfExtractionService.dropMarginNumbers("12" + PdfExtractionService.GAP + "Title text"))
                .isEqualTo("Title text");
        assertThat(PdfExtractionService.dropMarginNumbers("Left" + PdfExtractionService.GAP + "7" + PdfExtractionService.GAP + "Right"))
                .isEqualTo("Left" + PdfExtractionService.GAP + "Right");
        assertThat(PdfExtractionService.dropMarginNumbers("2022" + PdfExtractionService.GAP + "Title"))
                .isEqualTo("2022" + PdfExtractionService.GAP + "Title");
        assertThat(PdfExtractionService.dropMarginNumbers("42")).isEqualTo("42");
    }
}
