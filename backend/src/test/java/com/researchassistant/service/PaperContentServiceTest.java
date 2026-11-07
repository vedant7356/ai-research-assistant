package com.researchassistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.researchassistant.entity.Paper;
import com.researchassistant.repository.PaperRepository;
import com.researchassistant.support.FakeHttpServer;
import com.researchassistant.support.FakeHttpServer.Reply;
import com.researchassistant.support.TestPdfs;

class PaperContentServiceTest {

    private FakeHttpServer server;
    private PaperRepository repository;
    private PaperContentService service;

    @BeforeEach
    void start() {
        server = new FakeHttpServer();
        repository = mock(PaperRepository.class);
        service = new PaperContentService(new PdfExtractionService(), repository);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private Paper paperWithPdfUrl(String url) {
        Paper p = new Paper();
        p.setTitle("T");
        p.setPdfUrl(url);
        return p;
    }

    @Test
    void storedFullTextIsReturnedWithoutAnyDownload() {
        Paper p = paperWithPdfUrl(server.url() + "/paper.pdf");
        p.setFullText("already here");
        assertThat(service.fullTextOrNull(p)).isEqualTo("already here");
        assertThat(server.requestCount()).isZero();
    }

    @Test
    void paperWithoutPdfUrlHasNoFullText() {
        assertThat(service.fullTextOrNull(paperWithPdfUrl(null))).isNull();
        assertThat(service.fullTextOrNull(paperWithPdfUrl("   "))).isNull();
    }

    @Test
    void downloadsExtractsAndCachesTheOpenAccessPdf() {
        server.reply(Reply.bytes(200, "application/pdf", TestPdfs.typicalPaper()));
        Paper p = paperWithPdfUrl(server.url() + "/paper.pdf");
        String text = service.fullTextOrNull(p);
        assertThat(text).contains("Mobile applications handle sensitive user data");
        assertThat(p.getFullText()).isEqualTo(text);
        verify(repository).save(p);
    }

    @Test
    void nonHttpSchemesAreNeverFetched() {
        assertThat(service.fullTextOrNull(paperWithPdfUrl("file:///etc/passwd"))).isNull();
        assertThat(service.fullTextOrNull(paperWithPdfUrl("ftp://example.org/x.pdf"))).isNull();
        assertThat(service.fullTextOrNull(paperWithPdfUrl("not a url at all"))).isNull();
        verifyNoInteractions(repository);
    }

    @Test
    void failedOrUnusableDownloadsGiveNull() {
        server.reply(Reply.json(404, "{}"));
        assertThat(service.fullTextOrNull(paperWithPdfUrl(server.url() + "/missing.pdf"))).isNull();

        server.reply(Reply.bytes(200, "text/html", "<html>login required</html>".getBytes(StandardCharsets.UTF_8)));
        assertThat(service.fullTextOrNull(paperWithPdfUrl(server.url() + "/paywall"))).isNull();

        // A real PDF whose text is too short to be a paper (under 500 characters) is not useful either.
        server.reply(Reply.bytes(200, "application/pdf", TestPdfs.pdf(TestPdfs.line("Just a cover page", 12))));
        assertThat(service.fullTextOrNull(paperWithPdfUrl(server.url() + "/cover.pdf"))).isNull();
        verifyNoInteractions(repository);
    }
}
