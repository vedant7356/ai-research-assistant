package com.researchassistant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.jayway.jsonpath.JsonPath;
import com.researchassistant.entity.Paper;
import com.researchassistant.exception.ApiException;
import com.researchassistant.exception.GlobalExceptionHandler;

/**
 * Round 2: cross-layer contract. The shared fixture contract/error-envelope.json is checked against what the backend
 * really sends (here) and against what the frontend's ApiError parser makes of it (frontend/src/api/contract.test.ts).
 */
class ErrorContractTest extends ApiTestBase {

    private static final String FIXTURE;

    static {
        try {
            FIXTURE = Files.readString(Path.of("..", "contract", "error-envelope.json"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("run the tests from the backend folder: " + e.getMessage(), e);
        }
    }

    private static Object field(String sample, String path) {
        return JsonPath.read(FIXTURE, "$." + sample + "." + path);
    }

    private void matches(String sample, ResultActions actual) throws Exception {
        actual.andExpect(status().is((Integer) field(sample, "status")))
                .andExpect(jsonPath("$.error.code").value(field(sample, "body.error.code")))
                .andExpect(jsonPath("$.error.message").value(field(sample, "body.error.message")))
                .andExpect(jsonPath("$.error.retryable").value(field(sample, "body.error.retryable")))
                // exactly {error:{code,message,retryable}}, nothing extra for the frontend to trip over
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$.error.length()").value(3));
    }

    @Test
    void notInLibrary() throws Exception {
        matches("notInLibrary", mvc.perform(get("/api/library/987654")));
    }

    @Test
    void missingTitle() throws Exception {
        matches("missingTitle", mvc.perform(post("/api/library").contentType(MediaType.APPLICATION_JSON).content("{}")));
    }

    @Test
    void llmDown() throws Exception {
        Paper p = savedPaper("Paper", "This abstract is comfortably longer than forty characters in total.", "Some text.");
        when(llm.chat(anyString(), anyString(), anyInt()))
                .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "LLM_UNAVAILABLE", "Down.", true));
        matches("llmDown", mvc.perform(post("/api/library/" + p.getId() + "/ask")
                .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Why?\"}")));
    }

    @Test
    void unexpected() throws Exception {
        Paper p = savedPaper("Paper", "This abstract is comfortably longer than forty characters in total.", "Some text.");
        when(llm.chat(anyString(), anyString(), anyInt())).thenThrow(new IllegalStateException("boom"));
        matches("unexpected", mvc.perform(post("/api/library/" + p.getId() + "/ask")
                .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Why?\"}")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fileTooLarge() {
        var r = new GlobalExceptionHandler().handleTooLarge(new MaxUploadSizeExceededException(1));
        Map<String, Object> error = (Map<String, Object>) r.getBody().get("error");
        assertThat(r.getStatusCode().value()).isEqualTo((Integer) field("fileTooLarge", "status"));
        assertThat(error).containsEntry("code", field("fileTooLarge", "body.error.code"))
                .containsEntry("message", field("fileTooLarge", "body.error.message"))
                .containsEntry("retryable", field("fileTooLarge", "body.error.retryable"))
                .hasSize(3);
    }
}
