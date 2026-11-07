package com.researchassistant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.researchassistant.support.TestPdfs;

class UploadApiTest extends ApiTestBase {

    private ResultActions upload(String filename, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/uploads").file(new MockMultipartFile("file", filename, "application/pdf", bytes)));
    }

    @Test
    void uploadingARealPaperExtractsTextAndMetadata() throws Exception {
        upload("paper.pdf", TestPdfs.typicalPaper())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.pageCount").value(2))
                .andExpect(jsonPath("$.missingFields").isEmpty())
                .andExpect(jsonPath("$.item.source").value("UPLOAD"))
                .andExpect(jsonPath("$.item.title").value(TestPdfs.TITLE))
                .andExpect(jsonPath("$.item.authors[0]").value("Jane Doe"))
                .andExpect(jsonPath("$.item.authors[1]").value("John Smith"))
                .andExpect(jsonPath("$.item.year").value(2022))
                .andExpect(jsonPath("$.item.abstractText").value(org.hamcrest.Matchers.startsWith("Mobile applications handle sensitive user data")))
                .andExpect(jsonPath("$.item.textStatus").value("FULL_TEXT"))
                .andExpect(jsonPath("$.item.originalFilename").value("paper.pdf"))
                .andExpect(jsonPath("$.item.fullText").doesNotExist());
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findAll().get(0).getFullText()).contains("Smartphones are used for banking");
    }

    @Test
    void uploadedPaperAppearsInTheLibraryAndCanBeCorrected() throws Exception {
        upload("paper.pdf", TestPdfs.typicalPaper()).andExpect(status().isCreated());
        mvc.perform(get("/api/library?source=UPLOAD"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value(TestPdfs.TITLE));
        Long id = repository.findAll().get(0).getId();
        mvc.perform(patch("/api/library/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Corrected title\",\"authors\":[\"Real Author\"],\"year\":2020}"))
                .andExpect(jsonPath("$.title").value("Corrected title"))
                .andExpect(jsonPath("$.textStatus").value("FULL_TEXT"));
    }

    @Test
    void uploadingTheSameFileAgainReturns200AndDoesNotCreateAnotherRow() throws Exception {
        byte[] bytes = TestPdfs.typicalPaper();
        upload("paper.pdf", bytes).andExpect(status().isCreated());
        upload("renamed-copy.pdf", bytes).andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.item.originalFilename").value("paper.pdf"));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void windowsStylePathsAreReducedToTheFileName() throws Exception {
        upload("C:\\Users\\student\\papers\\survey.pdf", TestPdfs.typicalPaper())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.item.originalFilename").value("survey.pdf"));
    }

    @Test
    void textFilesAndImagesRenamedToPdfAreRejected() throws Exception {
        upload("notes.pdf", "just some plain text, not a pdf".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_FILE_TYPE"))
                .andExpect(jsonPath("$.error.retryable").value(false));
        upload("image.pdf", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0})
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_FILE_TYPE"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        upload("empty.pdf", new byte[0]).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EMPTY_FILE"))
                .andExpect(jsonPath("$.error.retryable").value(false));
    }

    @Test
    void requestWithoutAFilePartIsABadRequest() throws Exception {
        mvc.perform(multipart("/api/uploads")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("HTTP_400"))
                .andExpect(jsonPath("$.error.retryable").value(false));
    }

    @Test
    void scannedPdfWithoutTextIsRejectedWithAnOcrHint() throws Exception {
        upload("scan.pdf", TestPdfs.blank()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("EMPTY_PDF"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("OCR")));
        assertThat(repository.count()).isZero();
    }

    @Test
    void pdfWithTooLittleTextIsRejected() throws Exception {
        upload("tiny.pdf", TestPdfs.pdf(TestPdfs.line("Hello", 12))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("EMPTY_PDF"))
                .andExpect(jsonPath("$.error.retryable").value(false));
    }

    @Test
    void passwordProtectedPdfIsRejectedClearly() throws Exception {
        upload("locked.pdf", TestPdfs.encrypted(TestPdfs.line("Secret contents of a locked document.", 12)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_PROTECTED"))
                .andExpect(jsonPath("$.error.retryable").value(false));
    }

    @Test
    void damagedPdfIsRejectedClearly() throws Exception {
        upload("broken.pdf", "%PDF-1.4\nthis file claims to be a pdf but is not".getBytes(StandardCharsets.ISO_8859_1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INVALID_PDF"))
                .andExpect(jsonPath("$.error.retryable").value(false));
    }

    @Test
    void whenNothingCanBeGuessedTheFileNameBecomesTheTitleAndFieldsAreReportedMissing() throws Exception {
        upload("cover-only.pdf", TestPdfs.textOnSecondPageOnly())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.item.title").value("cover-only"))
                .andExpect(jsonPath("$.missingFields[0]").value("title"))
                .andExpect(jsonPath("$.missingFields[1]").value("authors"));
    }
}
