package com.researchassistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import com.researchassistant.exception.ApiException;
import com.researchassistant.exception.GlobalExceptionHandler;

/** Round 1 (coverage gap): the handler's 413 branch was never reached by the initial suite. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> error(ResponseEntity<Map<String, Object>> r) {
        return (Map<String, Object>) r.getBody().get("error");
    }

    @Test
    void oversizedUploadGives413WithTheDocumentedLimitAndIsNotRetryable() {
        ResponseEntity<Map<String, Object>> r = handler.handleTooLarge(new MaxUploadSizeExceededException(25L * 1024 * 1024));
        assertThat(r.getStatusCode().value()).isEqualTo(413);
        assertThat(error(r)).containsEntry("code", "FILE_TOO_LARGE").containsEntry("retryable", false);
        assertThat((String) error(r).get("message")).contains("25 MB");
    }

    @Test
    void apiExceptionsKeepTheirStatusCodeAndRetryFlag() {
        ResponseEntity<Map<String, Object>> r = handler.handleApi(
                new ApiException(HttpStatus.BAD_GATEWAY, "LLM_UNAVAILABLE", "try later", true));
        assertThat(r.getStatusCode().value()).isEqualTo(502);
        assertThat(error(r)).containsEntry("code", "LLM_UNAVAILABLE").containsEntry("message", "try later").containsEntry("retryable", true);
    }

    @Test
    void springClientErrorsKeepTheirStatusInsteadOfBecoming500() {
        ResponseEntity<Map<String, Object>> r = handler.handleOther(new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED));
        assertThat(r.getStatusCode().value()).isEqualTo(405);
        assertThat(error(r)).containsEntry("code", "HTTP_405").containsEntry("retryable", false);
    }

    @Test
    void unexpectedExceptionsBecomeAGeneric500ThatDoesNotLeakTheMessage() {
        ResponseEntity<Map<String, Object>> r = handler.handleOther(new IllegalStateException("secret internal detail"));
        assertThat(r.getStatusCode().value()).isEqualTo(500);
        assertThat(error(r)).containsEntry("code", "INTERNAL").containsEntry("retryable", true);
        assertThat((String) error(r).get("message")).doesNotContain("secret internal detail");
    }
}
