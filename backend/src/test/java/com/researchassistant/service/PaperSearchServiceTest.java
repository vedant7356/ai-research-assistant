package com.researchassistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.researchassistant.dto.PaperDto;
import com.researchassistant.dto.SearchResponse;
import com.researchassistant.exception.ApiException;
import com.researchassistant.support.FakeHttpServer;
import com.researchassistant.support.FakeHttpServer.Reply;

class PaperSearchServiceTest {

    private static final String ONE_WORK = """
            {"meta":{"count":1234},"results":[{
              "id":"https://openalex.org/W123",
              "doi":"https://doi.org/10.1/abc",
              "title":"Mobile Security Survey",
              "publication_year":2021,
              "authorships":[{"author":{"display_name":"Ann Lee"}},{"author":{"display_name":"Bo Chan"}}],
              "abstract_inverted_index":{"Phones":[0],"are":[1],"risky":[2]},
              "primary_location":{"landing_page_url":"https://example.org/paper","pdf_url":"https://example.org/primary.pdf"},
              "best_oa_location":{"pdf_url":"https://example.org/best.pdf"},
              "open_access":{"is_oa":true}
            }]}""";

    private FakeHttpServer server;
    private PaperSearchService service;

    @BeforeEach
    void start() {
        server = new FakeHttpServer();
        service = new PaperSearchService(server.url(), "");
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private static Map<String, String> params(String rawQuery) {
        Map<String, String> out = new HashMap<>();
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            out.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    @Test
    void mapsAnOpenAlexWorkToAPaper() {
        server.reply(Reply.json(200, ONE_WORK));
        SearchResponse r = service.search("  mobile security ", null, false, false, 1, 10);
        assertThat(r.query()).isEqualTo("mobile security");
        assertThat(r.total()).isEqualTo(1234);
        assertThat(r.page()).isEqualTo(1);
        assertThat(r.limit()).isEqualTo(10);
        PaperDto p = r.items().get(0);
        assertThat(p.externalId()).isEqualTo("W123");
        assertThat(p.title()).isEqualTo("Mobile Security Survey");
        assertThat(p.authors()).containsExactly("Ann Lee", "Bo Chan");
        assertThat(p.year()).isEqualTo(2021);
        assertThat(p.abstractText()).isEqualTo("Phones are risky");
        assertThat(p.url()).isEqualTo("https://example.org/paper");
        assertThat(p.pdfUrl()).isEqualTo("https://example.org/best.pdf");
        assertThat(p.openAccess()).isTrue();
        assertThat(p.inLibrary()).isFalse();
    }

    @Test
    void fallsBackWhenFieldsAreMissing() {
        server.reply(Reply.json(200, """
                {"meta":{"count":1},"results":[{"id":"https://openalex.org/W9","doi":"https://doi.org/10.1/x",
                 "primary_location":{"pdf_url":"https://example.org/primary.pdf"}}]}"""));
        PaperDto p = service.search("x", null, false, false, 1, 10).items().get(0);
        assertThat(p.title()).isEqualTo("Untitled");
        assertThat(p.authors()).isEmpty();
        assertThat(p.year()).isNull();
        assertThat(p.abstractText()).isNull();
        assertThat(p.url()).isEqualTo("https://doi.org/10.1/x");
        assertThat(p.pdfUrl()).isEqualTo("https://example.org/primary.pdf");
        assertThat(p.openAccess()).isFalse();
    }

    @Test
    void urlFallsBackToTheOpenAlexIdWhenThereIsNothingElse() {
        server.reply(Reply.json(200, "{\"meta\":{\"count\":1},\"results\":[{\"id\":\"https://openalex.org/W5\",\"title\":\"T\"}]}"));
        PaperDto p = service.search("x", null, false, false, 1, 10).items().get(0);
        assertThat(p.url()).isEqualTo("https://openalex.org/W5");
        assertThat(p.pdfUrl()).isNull();
    }

    @Test
    void sendsTheExpectedRequest() {
        server.reply(Reply.json(200, ONE_WORK));
        service.search("rag", null, false, false, 2, 5);
        assertThat(server.lastPath()).isEqualTo("/works");
        Map<String, String> q = params(server.lastRawQuery());
        assertThat(q).containsEntry("search", "rag").containsEntry("per-page", "5").containsEntry("page", "2");
        assertThat(q.get("select")).contains("title").contains("abstract_inverted_index");
        assertThat(q).doesNotContainKeys("filter", "sort", "api_key");
    }

    @Test
    void yearOpenAccessSortAndApiKeyBecomeOpenAlexParameters() {
        PaperSearchService keyed = new PaperSearchService(server.url(), "secret-key");
        server.reply(Reply.json(200, ONE_WORK));
        keyed.search("rag", 2015, true, true, 1, 10);
        Map<String, String> q = params(server.lastRawQuery());
        assertThat(q).containsEntry("filter", "from_publication_date:2015-01-01,is_oa:true");
        assertThat(q).containsEntry("sort", "publication_date:desc");
        assertThat(q).containsEntry("api_key", "secret-key");
    }

    @Test
    void specialCharactersInTheQuerySurviveEncoding() {
        server.reply(Reply.json(200, ONE_WORK));
        service.search("C++ & #1 100% [x]", null, false, false, 1, 10);
        assertThat(params(server.lastRawQuery())).containsEntry("search", "C++ & #1 100% [x]");
    }

    @Test
    void pageAndLimitAreClamped() {
        server.reply(Reply.json(200, ONE_WORK));
        SearchResponse low = service.search("x", null, false, false, 0, 0);
        assertThat(low.page()).isEqualTo(1);
        assertThat(low.limit()).isEqualTo(1);
        SearchResponse high = service.search("x", null, false, false, -4, 100);
        assertThat(high.page()).isEqualTo(1);
        assertThat(high.limit()).isEqualTo(25);
        assertThat(params(server.lastRawQuery())).containsEntry("per-page", "25").containsEntry("page", "1");
    }

    @Test
    void blankQueryIsRejectedWithoutCallingOpenAlex() {
        ApiException blank = assertThrows(ApiException.class, () -> service.search("   ", null, false, false, 1, 10));
        assertThat(blank.getCode()).isEqualTo("EMPTY_QUERY");
        assertThat(blank.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThrows(ApiException.class, () -> service.search(null, null, false, false, 1, 10));
        assertThat(server.requestCount()).isZero();
    }

    @Test
    void rateLimitResponsesAreRetryable() {
        for (int status : new int[] {429, 402}) {
            server.reply(Reply.json(status, "{}"));
            ApiException e = assertThrows(ApiException.class, () -> service.search("x", null, false, false, 1, 10));
            assertThat(e.getCode()).isEqualTo("RATE_LIMITED");
            assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(e.isRetryable()).isTrue();
        }
    }

    @Test
    void authFailuresAreNotRetryable() {
        for (int status : new int[] {401, 403}) {
            server.reply(Reply.json(status, "{}"));
            ApiException e = assertThrows(ApiException.class, () -> service.search("x", null, false, false, 1, 10));
            assertThat(e.getCode()).isEqualTo("SEARCH_AUTH_FAILED");
            assertThat(e.isRetryable()).isFalse();
        }
    }

    @Test
    void otherUpstreamErrorsAreReportedAsBadGateway() {
        server.reply(Reply.json(500, "{}"));
        ApiException e = assertThrows(ApiException.class, () -> service.search("x", null, false, false, 1, 10));
        assertThat(e.getCode()).isEqualTo("SEARCH_UPSTREAM_FAILED");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(e.isRetryable()).isTrue();
    }

    @Test
    void unreachableServerIsReportedAsBadGateway() {
        PaperSearchService dead = new PaperSearchService("http://127.0.0.1:1", "");
        ApiException e = assertThrows(ApiException.class, () -> dead.search("x", null, false, false, 1, 10));
        assertThat(e.getCode()).isEqualTo("SEARCH_UPSTREAM_FAILED");
        assertThat(e.isRetryable()).isTrue();
    }

    @Test
    void responseWithoutResultsGivesAnEmptyPage() {
        server.reply(Reply.json(200, "{}"));
        SearchResponse r = service.search("x", null, false, false, 1, 10);
        assertThat(r.total()).isZero();
        assertThat(r.items()).isEmpty();
    }

    @Test
    void rebuildAbstractPutsWordsBackInOrder() {
        Map<String, Object> inverted = new HashMap<>();
        inverted.put("world", java.util.List.of(1));
        inverted.put("Hello", java.util.List.of(0));
        inverted.put("again", java.util.List.of(2, 4));
        inverted.put("hello", java.util.List.of(3));
        assertThat(PaperSearchService.rebuildAbstract(inverted)).isEqualTo("Hello world again hello again");
    }

    @Test
    void rebuildAbstractHandlesEmptyAndGappyInput() {
        assertThat(PaperSearchService.rebuildAbstract(new HashMap<>())).isNull();
        Map<String, Object> gappy = new HashMap<>();
        gappy.put("first", java.util.List.of(0));
        gappy.put("last", java.util.List.of(5));
        assertThat(PaperSearchService.rebuildAbstract(gappy)).isEqualTo("first last");
        Map<String, Object> junk = new HashMap<>();
        junk.put("word", java.util.List.of("not-a-number"));
        assertThat(PaperSearchService.rebuildAbstract(junk)).isNull();
    }
}
