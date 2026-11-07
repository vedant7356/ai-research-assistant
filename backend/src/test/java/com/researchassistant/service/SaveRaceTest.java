package com.researchassistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

import com.researchassistant.entity.Paper;
import com.researchassistant.entity.PaperSource;
import com.researchassistant.dto.SavePaperRequest;
import com.researchassistant.repository.PaperRepository;
import com.researchassistant.service.PdfExtractionService.ExtractedPdf;
import com.researchassistant.service.UploadService.UploadResult;

/**
 * Round 1 (coverage gap): the "two requests at once" fallbacks in LibraryService.save and UploadService.upload.
 * The unique constraint rejects the second insert; the service must hand back the row the first request created.
 */
class SaveRaceTest {

    private static Paper existing(String title) {
        Paper p = new Paper();
        p.setSource(PaperSource.SAVED);
        p.setTitle(title);
        return p;
    }

    private static SavePaperRequest request(String externalId) {
        return new SavePaperRequest(externalId, "Racy paper", List.of("Ann Lee"), 2020, null, null, null, false);
    }

    @Test
    void secondConcurrentSaveReturnsTheFirstRowAndReportsNotCreated() {
        PaperRepository repo = mock(PaperRepository.class);
        when(repo.findByExternalId("W1")).thenReturn(Optional.empty()).thenReturn(Optional.of(existing("Racy paper")));
        when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique external_id"));

        LibraryService.SaveResult r = new LibraryService(repo).save(request("W1"));

        assertThat(r.created()).isFalse();
        assertThat(r.item().title()).isEqualTo("Racy paper");
    }

    @Test
    void integrityErrorWithNoRowToFallBackOnIsNotSwallowed() {
        PaperRepository repo = mock(PaperRepository.class);
        when(repo.findByExternalId("W1")).thenReturn(Optional.empty());
        when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("something else"));

        assertThatThrownBy(() -> new LibraryService(repo).save(request("W1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void secondConcurrentUploadOfTheSameFileReturnsTheFirstRowAsDuplicate() {
        PaperRepository repo = mock(PaperRepository.class);
        PdfExtractionService extraction = mock(PdfExtractionService.class);
        Paper first = existing("First upload");
        first.setSource(PaperSource.UPLOAD);
        when(repo.findByContentHash(any())).thenReturn(Optional.empty()).thenReturn(Optional.of(first));
        when(extraction.extract(any())).thenReturn(new ExtractedPdf("x".repeat(500), 1, null, null, null, List.of()));
        when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique content_hash"));

        byte[] bytes = "%PDF-1.4 fake body".getBytes(StandardCharsets.ISO_8859_1);
        UploadResult r = new UploadService(extraction, repo).upload(new MockMultipartFile("file", "a.pdf", "application/pdf", bytes));

        assertThat(r.created()).isFalse();
        assertThat(r.response().duplicate()).isTrue();
        assertThat(r.response().item().title()).isEqualTo("First upload");
    }
}
