package com.researchassistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.researchassistant.exception.ApiException;
import com.researchassistant.service.PdfExtractionService.ExtractedPdf;
import com.researchassistant.support.TestPdfs;

class PdfExtractionServiceTest {

    private final PdfExtractionService service = new PdfExtractionService();

    @Test
    void extractsTextPageCountAndFirstPageLinesWithFontSizes() {
        ExtractedPdf pdf = service.extract(TestPdfs.typicalPaper());
        assertThat(pdf.pageCount()).isEqualTo(2);
        assertThat(pdf.fullText()).contains("Abstract").contains("Mobile applications handle sensitive user data");
        assertThat(pdf.fullText()).doesNotContain("\u0000");
        assertThat(pdf.firstPageLines().get(0).text()).isEqualTo(TestPdfs.TITLE);
        assertThat(pdf.firstPageLines().get(0).fontSize()).isCloseTo(22f, within(0.5f));
    }

    @Test
    void firstPageLinesOnlyComeFromPageOne() {
        ExtractedPdf pdf = service.extract(TestPdfs.typicalPaper());
        assertThat(pdf.firstPageLines()).noneMatch(line -> line.text().contains("Section two line"));
    }

    @Test
    void pdfWithoutTextGivesBlankTextAndNoLines() {
        ExtractedPdf pdf = service.extract(TestPdfs.blank());
        assertThat(pdf.fullText().isBlank()).isTrue();
        assertThat(pdf.firstPageLines()).isEmpty();
        assertThat(pdf.pageCount()).isEqualTo(1);
    }

    @Test
    void lineThatIsOnlyANumberIsTreatedAsMarginNoiseAndSkipped() {
        ExtractedPdf pdf = service.extract(TestPdfs.pdf(TestPdfs.line("12", 10), TestPdfs.line("Real First Line", 12)));
        assertThat(pdf.firstPageLines()).extracting(l -> l.text()).containsExactly("Real First Line");
    }

    @Test
    void damagedPdfIsReportedAsInvalid() {
        byte[] junk = "%PDF-1.4\nthis is not really a pdf".getBytes(StandardCharsets.ISO_8859_1);
        ApiException e = assertThrows(ApiException.class, () -> service.extract(junk));
        assertThat(e.getCode()).isEqualTo("INVALID_PDF");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void passwordProtectedPdfIsReportedClearly() {
        byte[] locked = TestPdfs.encrypted(TestPdfs.line("Secret contents", 12));
        ApiException e = assertThrows(ApiException.class, () -> service.extract(locked));
        assertThat(e.getCode()).isEqualTo("PASSWORD_PROTECTED");
        assertThat(e.isRetryable()).isFalse();
    }

    @Test
    void dropMarginNumbersRemovesLineNumbersNextToText() {
        String gap = PdfExtractionService.GAP;
        assertThat(PdfExtractionService.dropMarginNumbers("12" + gap + "Some text" + gap + "13")).isEqualTo("Some text");
        assertThat(PdfExtractionService.dropMarginNumbers("42")).isEqualTo("42");
        assertThat(PdfExtractionService.dropMarginNumbers("Plain line")).isEqualTo("Plain line");
        assertThat(PdfExtractionService.dropMarginNumbers("Left" + gap + "Right")).isEqualTo("Left" + gap + "Right");
    }
}
