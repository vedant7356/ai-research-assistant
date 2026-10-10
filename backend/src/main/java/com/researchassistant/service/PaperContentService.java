package com.researchassistant.service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.researchassistant.entity.Paper;
import com.researchassistant.repository.PaperRepository;

/** Gets the best available text for a paper: stored full text, else the open-access PDF (downloaded once and cached). */
@Service
public class PaperContentService {

    private static final Logger log = LoggerFactory.getLogger(PaperContentService.class);
    private static final int MAX_PDF_BYTES = 25 * 1024 * 1024;
    private static final int MIN_USEFUL_CHARS = 500;

    private final PdfExtractionService pdfExtraction;
    private final PaperRepository repository;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public PaperContentService(PdfExtractionService pdfExtraction, PaperRepository repository) {
        this.pdfExtraction = pdfExtraction;
        this.repository = repository;
    }

    /** Returns the paper's full text, or null when none is available (caller then falls back to the abstract). */
    public String fullTextOrNull(Paper p) {
        if (p.getFullText() != null && !p.getFullText().isBlank()) return p.getFullText();
        String pdfUrl = p.getPdfUrl();
        if (pdfUrl == null || pdfUrl.isBlank()) return null;
        try {
            URI uri = URI.create(pdfUrl.trim());
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return null;
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(40))
                    .header("Accept", "application/pdf").GET().build();
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                log.info("PDF download for paper {} returned HTTP {}", p.getId(), resp.statusCode());
                return null;
            }
            byte[] bytes;
            try (InputStream in = resp.body()) {
                bytes = in.readNBytes(MAX_PDF_BYTES + 1);
            }
            if (bytes.length > MAX_PDF_BYTES || bytes.length < 5 || !new String(bytes, 0, 5).equals("%PDF-")) {
                log.info("Download for paper {} was not a usable PDF", p.getId());
                return null;
            }
            String text = pdfExtraction.extract(bytes).fullText();
            if (text == null || text.strip().length() < MIN_USEFUL_CHARS) return null;
            p.setFullText(text);
            repository.save(p);
            return text;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.info("Full text fetch failed for paper {}: {}", p.getId(), e.getMessage());
            return null;
        }
    }
}
