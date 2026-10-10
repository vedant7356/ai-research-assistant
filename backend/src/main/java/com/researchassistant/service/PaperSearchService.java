package com.researchassistant.service;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.researchassistant.dto.PaperDto;
import com.researchassistant.dto.SearchResponse;
import com.researchassistant.exception.ApiException;

/**
 * Calls the OpenAlex "works" API and converts its (large, nested) JSON into our small PaperDto.
 * JSON is read as plain Maps/Lists on purpose: no extra mapping classes to maintain.
 */
@Service
public class PaperSearchService {

    private static final Logger log = LoggerFactory.getLogger(PaperSearchService.class);

    // Only ask OpenAlex for the fields we use (smaller, faster responses).
    private static final String SELECT =
            "id,doi,title,publication_year,authorships,abstract_inverted_index,primary_location,best_oa_location,open_access";

    private final RestClient client;
    private final String baseUrl;
    private final String apiKey;

    public PaperSearchService(@Value("${openalex.base-url}") String baseUrl,
                              @Value("${openalex.api-key:}") String apiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public SearchResponse search(String query, Integer yearFrom, boolean newestFirst,
                                 boolean openAccessOnly, int page, int limit) {
        if (query == null || query.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_QUERY",
                    "Enter a topic, title, author or keyword to search.", false);
        }
        int safePage = Math.max(page, 1);
        int safeLimit = Math.min(Math.max(limit, 1), 25);
        String q = query.trim();

        List<String> filters = new ArrayList<>();
        if (yearFrom != null) filters.add("from_publication_date:" + yearFrom + "-01-01");
        if (openAccessOnly) filters.add("is_oa:true");

        // encode() + buildAndExpand() encodes user text strictly (so '&', '#', spaces cannot break the URL).
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(baseUrl).path("/works")
                .queryParam("search", "{q}")
                .queryParam("select", SELECT)
                .queryParam("per-page", safeLimit)
                .queryParam("page", safePage);
        if (!filters.isEmpty()) b.queryParam("filter", "{filter}");
        if (newestFirst) b.queryParam("sort", "publication_date:desc");
        if (!apiKey.isBlank()) b.queryParam("api_key", "{key}");

        Map<String, Object> vars = new java.util.HashMap<>();
        vars.put("q", q);
        vars.put("filter", String.join(",", filters));
        vars.put("key", apiKey);
        URI uri = b.encode().buildAndExpand(vars).toUri();

        Map<String, Object> body;
        try {
            body = client.get().uri(uri).retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("OpenAlex returned HTTP {}", status);
            if (status == 429 || status == 402) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                        "The paper search limit was reached. Wait a moment and try again.", true);
            }
            if (status == 401 || status == 403) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "SEARCH_AUTH_FAILED",
                        "The paper search service rejected the request. Check OPENALEX_API_KEY.", false);
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SEARCH_UPSTREAM_FAILED",
                    "The paper search service returned an error. Try again shortly.", true);
        } catch (RestClientException e) {
            log.warn("OpenAlex unreachable: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SEARCH_UPSTREAM_FAILED",
                    "The paper search service didn't respond. Try again shortly.", true);
        }

        long total = 0;
        Object count = asMap(body == null ? null : body.get("meta")).get("count");
        if (count instanceof Number n) total = n.longValue();

        List<PaperDto> items = new ArrayList<>();
        for (Object o : asList(body == null ? null : body.get("results"))) {
            items.add(toPaper(asMap(o)));
        }
        return new SearchResponse(q, total, safePage, safeLimit, items);
    }

    private PaperDto toPaper(Map<String, Object> w) {
        String id = str(w.get("id"));
        String externalId = id == null ? null : id.substring(id.lastIndexOf('/') + 1);

        List<String> authors = new ArrayList<>();
        for (Object a : asList(w.get("authorships"))) {
            String name = str(asMap(asMap(a).get("author")).get("display_name"));
            if (name != null) authors.add(name);
        }

        Map<String, Object> primary = asMap(w.get("primary_location"));
        Map<String, Object> bestOa = asMap(w.get("best_oa_location"));
        String url = firstNonNull(str(primary.get("landing_page_url")), str(w.get("doi")), id);
        String pdfUrl = firstNonNull(str(bestOa.get("pdf_url")), str(primary.get("pdf_url")));

        Integer year = w.get("publication_year") instanceof Number n ? n.intValue() : null;
        boolean openAccess = Boolean.TRUE.equals(asMap(w.get("open_access")).get("is_oa"));
        String title = str(w.get("title"));

        return new PaperDto(externalId, title == null ? "Untitled" : title, authors, year,
                rebuildAbstract(asMap(w.get("abstract_inverted_index"))), url, pdfUrl, openAccess, false);
    }

    /** OpenAlex ships abstracts as {word: [positions]}; put the words back in order. */
    static String rebuildAbstract(Map<String, Object> inverted) {
        if (inverted.isEmpty()) return null;
        int max = -1;
        for (Object positions : inverted.values()) {
            for (Object p : asList(positions)) {
                if (p instanceof Number n) max = Math.max(max, n.intValue());
            }
        }
        if (max < 0) return null;
        String[] words = new String[max + 1];
        for (Map.Entry<String, Object> e : inverted.entrySet()) {
            for (Object p : asList(e.getValue())) {
                if (p instanceof Number n && n.intValue() >= 0 && n.intValue() <= max) words[n.intValue()] = e.getKey();
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w == null) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(w);
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        return o instanceof List<?> l ? (List<Object>) l : Collections.emptyList();
    }

    private static String str(Object o) {
        return o instanceof String s && !s.isBlank() ? s : null;
    }

    private static String firstNonNull(String... values) {
        for (String v : values) if (v != null) return v;
        return null;
    }
}
