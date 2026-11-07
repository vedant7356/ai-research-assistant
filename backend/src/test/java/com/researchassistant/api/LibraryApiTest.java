package com.researchassistant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.researchassistant.entity.Paper;
import com.researchassistant.entity.PaperSource;

class LibraryApiTest extends ApiTestBase {

    private static final String PAPER_JSON = """
            {"externalId":"W1","title":"  Mobile Security  ","authors":["Ann Lee","Bo Chan"],"year":2021,
             "abstractText":"An abstract.","url":"https://example.org/p","pdfUrl":null,"openAccess":true}""";

    private org.springframework.test.web.servlet.ResultActions postJson(String body) throws Exception {
        return mvc.perform(post("/api/library").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // ---- save ----

    @Test
    void savingANewPaperReturns201AndTheStoredItem() throws Exception {
        postJson(PAPER_JSON)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.source").value("SAVED"))
                .andExpect(jsonPath("$.title").value("Mobile Security"))
                .andExpect(jsonPath("$.authors[0]").value("Ann Lee"))
                .andExpect(jsonPath("$.year").value(2021))
                .andExpect(jsonPath("$.openAccess").value(true))
                .andExpect(jsonPath("$.textStatus").value("ABSTRACT_ONLY"))
                .andExpect(jsonPath("$.hasSummary").value(false));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void savingTheSamePaperTwiceReturns200AndKeepsOneRow() throws Exception {
        postJson(PAPER_JSON).andExpect(status().isCreated());
        postJson(PAPER_JSON).andExpect(status().isOk());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void aPaperNeedsATitle() throws Exception {
        postJson("{\"title\":\"   \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.retryable").value(false));
        postJson("{\"externalId\":\"W2\"}").andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @Test
    void missingOptionalFieldsAreAccepted() throws Exception {
        postJson("{\"title\":\"Only a title\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.openAccess").value(false))
                .andExpect(jsonPath("$.authors").isEmpty());
    }

    @Test
    void missingOrMalformedBodyIsABadRequest() throws Exception {
        mvc.perform(post("/api/library").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        postJson("{not json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    // ---- list ----

    @Test
    void listIsEmptyWhenNothingIsSaved() throws Exception {
        mvc.perform(get("/api/library")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void listDefaultsToNewestFirst() throws Exception {
        savedPaper("Older", PaperSource.SAVED, 2000);
        Thread.sleep(5);
        savedPaper("Newer", PaperSource.SAVED, 2000);
        mvc.perform(get("/api/library"))
                .andExpect(jsonPath("$.items[0].title").value("Newer"))
                .andExpect(jsonPath("$.items[1].title").value("Older"));
    }

    @Test
    void listCanBeSortedByTitleIgnoringCase() throws Exception {
        savedPaper("banana", PaperSource.SAVED, 2000);
        savedPaper("Apple", PaperSource.SAVED, 2000);
        savedPaper("cherry", PaperSource.SAVED, 2000);
        mvc.perform(get("/api/library?sort=title"))
                .andExpect(jsonPath("$.items[0].title").value("Apple"))
                .andExpect(jsonPath("$.items[1].title").value("banana"))
                .andExpect(jsonPath("$.items[2].title").value("cherry"));
    }

    @Test
    void listSortedByYearPutsNewestFirstAndUnknownYearsLast() throws Exception {
        savedPaper("No year", PaperSource.SAVED, null);
        savedPaper("Y2010", PaperSource.SAVED, 2010);
        savedPaper("Y2022", PaperSource.SAVED, 2022);
        mvc.perform(get("/api/library?sort=year"))
                .andExpect(jsonPath("$.items[0].title").value("Y2022"))
                .andExpect(jsonPath("$.items[1].title").value("Y2010"))
                .andExpect(jsonPath("$.items[2].title").value("No year"));
    }

    @Test
    void listFiltersBySourceAndUnknownSourceMeansAll() throws Exception {
        savedPaper("From search", PaperSource.SAVED, 2020);
        savedPaper("From upload", PaperSource.UPLOAD, 2020);
        mvc.perform(get("/api/library?source=SAVED")).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("From search"));
        mvc.perform(get("/api/library?source=upload")).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("From upload"));
        mvc.perform(get("/api/library?source=whatever")).andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void listSearchMatchesTitleOrAuthorCaseInsensitively() throws Exception {
        Paper a = savedPaper("Deep Learning", PaperSource.SAVED, 2020);
        a.setAuthorList(java.util.List.of("Geoffrey Hinton"));
        repository.saveAndFlush(a);
        savedPaper("Compilers", PaperSource.SAVED, 2020);
        mvc.perform(get("/api/library?q=DEEP")).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/library?q=hinton")).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Deep Learning"));
        mvc.perform(get("/api/library?q=nomatch")).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/library").param("q", "  ")).andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void listNeverExposesTheFullText() throws Exception {
        savedPaper("With text", "An abstract that is long enough to count.", "SECRET FULL TEXT");
        mvc.perform(get("/api/library"))
                .andExpect(jsonPath("$.items[0].textStatus").value("FULL_TEXT"))
                .andExpect(jsonPath("$.items[0].fullText").doesNotExist());
    }

    // ---- get / update / delete ----

    @Test
    void getReturnsOnePaperOr404() throws Exception {
        Paper p = savedPaper("Findable", PaperSource.SAVED, 2020);
        mvc.perform(get("/api/library/" + p.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Findable"));
        mvc.perform(get("/api/library/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_IN_LIBRARY"));
    }

    @Test
    void nonNumericIdIsABadRequest() throws Exception {
        mvc.perform(get("/api/library/abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("Invalid value for 'id'."));
    }

    @Test
    void patchCorrectsMetadataAndCleansTheAuthorList() throws Exception {
        Paper p = savedPaper("Wrong title", PaperSource.UPLOAD, 1999);
        mvc.perform(patch("/api/library/" + p.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  Right title \",\"authors\":[\" Ann Lee \",\"\",\"  \",\"Bo Chan\"],\"year\":2020,\"abstractText\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Right title"))
                .andExpect(jsonPath("$.authors.length()").value(2))
                .andExpect(jsonPath("$.authors[0]").value("Ann Lee"))
                .andExpect(jsonPath("$.year").value(2020))
                .andExpect(jsonPath("$.abstractText").doesNotExist());
    }

    @Test
    void patchValidatesTitleAndYear() throws Exception {
        Paper p = savedPaper("Keep me", PaperSource.UPLOAD, 2000);
        String url = "/api/library/" + p.getId();
        for (String bad : new String[] {
                "{\"title\":\"  \"}", "{\"authors\":[]}", "{\"title\":\"T\",\"year\":1899}", "{\"title\":\"T\",\"year\":2101}"}) {
            mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
        mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"T\",\"year\":1900}")).andExpect(status().isOk());
        mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"T\",\"year\":2100}")).andExpect(status().isOk());
        mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"T\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.year").doesNotExist());
    }

    @Test
    void patchOnAnUnknownPaperIs404() throws Exception {
        mvc.perform(patch("/api/library/424242").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"T\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_IN_LIBRARY"));
    }

    @Test
    void deleteRemovesThePaperAndASecondDeleteIs404() throws Exception {
        Paper p = savedPaper("Delete me", PaperSource.SAVED, 2020);
        mvc.perform(delete("/api/library/" + p.getId())).andExpect(status().isNoContent());
        assertThat(repository.existsById(p.getId())).isFalse();
        mvc.perform(delete("/api/library/" + p.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/library/" + p.getId())).andExpect(status().isNotFound());
    }

    // ---- error envelope for requests that never reach our code ----

    @Test
    void unknownRoutesAndWrongMethodsUseTheSameErrorEnvelope() throws Exception {
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("HTTP_404"))
                .andExpect(jsonPath("$.error.retryable").value(false));
        mvc.perform(post("/api/health")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("HTTP_405"));
    }

    @Test
    void healthEndpointReportsBackendAndDatabaseUp() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.time").isString());
    }
}
