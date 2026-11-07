package com.researchassistant.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.researchassistant.dto.PaperDto;
import com.researchassistant.dto.SearchResponse;
import com.researchassistant.entity.Paper;
import com.researchassistant.entity.PaperSource;
import com.researchassistant.exception.ApiException;

class SearchApiTest extends ApiTestBase {

    private static PaperDto dto(String externalId, String title) {
        return new PaperDto(externalId, title, List.of("Ann Lee"), 2021, "abstract", "https://example.org", null, false, false);
    }

    @Test
    void passesQueryAndFiltersToTheSearchService() throws Exception {
        when(searchService.search("rag", 2015, true, true, 2, 5))
                .thenReturn(new SearchResponse("rag", 42, 2, 5, List.of(dto("W1", "One"))));
        mvc.perform(get("/api/papers/search?q=rag&yearFrom=2015&sort=NEWEST&openAccessOnly=true&page=2&limit=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("rag"))
                .andExpect(jsonPath("$.total").value(42))
                .andExpect(jsonPath("$.items[0].title").value("One"));
    }

    @Test
    void usesDocumentedDefaultsWhenOptionalParametersAreMissing() throws Exception {
        when(searchService.search("rag", null, false, false, 1, 10))
                .thenReturn(new SearchResponse("rag", 0, 1, 10, List.of()));
        mvc.perform(get("/api/papers/search?q=rag")).andExpect(status().isOk());
        verify(searchService).search(eq("rag"), isNull(), eq(false), eq(false), eq(1), eq(10));
    }

    @Test
    void marksResultsThatAreAlreadyInTheLibrary() throws Exception {
        Paper p = savedPaper("Saved one", PaperSource.SAVED, 2020);
        p.setExternalId("W1");
        repository.saveAndFlush(p);
        when(searchService.search("x", null, false, false, 1, 10))
                .thenReturn(new SearchResponse("x", 2, 1, 10, List.of(dto("W1", "Saved one"), dto("W2", "New one"))));
        mvc.perform(get("/api/papers/search?q=x"))
                .andExpect(jsonPath("$.items[0].inLibrary").value(true))
                .andExpect(jsonPath("$.items[1].inLibrary").value(false));
    }

    @Test
    void resultsWithoutIdsDoNotBreakTheSearch() throws Exception {
        when(searchService.search("x", null, false, false, 1, 10))
                .thenReturn(new SearchResponse("x", 1, 1, 10, List.of(dto(null, "No id"))));
        mvc.perform(get("/api/papers/search?q=x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].inLibrary").value(false));
    }

    @Test
    void anEmptyResultPageIsFine() throws Exception {
        when(searchService.search("zzz", null, false, false, 1, 10))
                .thenReturn(new SearchResponse("zzz", 0, 1, 10, List.of()));
        mvc.perform(get("/api/papers/search?q=zzz")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void invalidNumbersAreRejectedAsValidationErrors() throws Exception {
        mvc.perform(get("/api/papers/search?q=x&yearFrom=abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("Invalid value for 'yearFrom'."));
        mvc.perform(get("/api/papers/search?q=x&page=first")).andExpect(status().isBadRequest());
    }

    @Test
    void serviceErrorsKeepTheirStatusAndRetryFlag() throws Exception {
        when(searchService.search("x", null, false, false, 1, 10))
                .thenThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Slow down.", true));
        mvc.perform(get("/api/papers/search?q=x")).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.error.message").value("Slow down."))
                .andExpect(jsonPath("$.error.retryable").value(true));
    }

    @Test
    void unexpectedFailuresBecomeAGeneric500WithoutLeakingDetails() throws Exception {
        when(searchService.search("x", null, false, false, 1, 10)).thenThrow(new IllegalStateException("db password is hunter2"));
        mvc.perform(get("/api/papers/search?q=x")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL"))
                .andExpect(jsonPath("$.error.message").value("Something went wrong on the server."))
                .andExpect(jsonPath("$.error.retryable").value(true));
    }
}
