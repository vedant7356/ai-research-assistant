package com.researchassistant.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.researchassistant.exception.ApiException;

/**
 * Thin client for an OpenAI-style chat endpoint (Purdue GenAI Studio: POST {base}/chat/completions).
 * The key comes from the environment (.env) and is never logged or sent to the browser.
 */
@Service
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() { };

    private final RestClient client;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public LlmClient(@Value("${llm.base-url:}") String baseUrl,
                     @Value("${llm.api-key:}") String apiKey,
                     @Value("${llm.model:}") String model) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(180));
        this.client = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    /** Sends one system + one user message and returns the assistant's answer text. */
    @SuppressWarnings("unchecked")
    public String chat(String system, String user, int maxTokens) {
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "LLM_NOT_CONFIGURED",
                    "The AI service is not configured. Set LLM_BASE_URL, LLM_API_KEY and LLM_MODEL in .env and restart the backend.", false);
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "max_tokens", maxTokens,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));
        Map<String, Object> response;
        try {
            response = client.post().uri(baseUrl + "/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MAP_TYPE);
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            log.warn("LLM call failed: HTTP {}", code);
            if (code == 401 || code == 403) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM_AUTH_FAILED",
                        "The AI service rejected the API key. Check LLM_API_KEY in .env.", false);
            }
            if (code == 429) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                        "The AI service is busy (20 requests per minute limit). Wait a minute and try again.", true);
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM_UPSTREAM_FAILED",
                    "The AI service returned an error (HTTP " + code + "). Try again.", true);
        } catch (RestClientException e) {
            log.warn("LLM call failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM_UNAVAILABLE",
                    "The AI service could not be reached or timed out. Check your network (Purdue VPN) and try again.", true);
        }
        if (response == null) {
            // GenAI Studio can answer with JSON null instead of an HTTP error when its rate limit is hit.
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "The AI service returned no answer (likely the 20 requests per minute limit). Wait a minute and try again.", true);
        }
        String content = null;
        Object choices = response.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first
                && first.get("message") instanceof Map<?, ?> message && message.get("content") instanceof String s) {
            content = s;
        }
        if (content == null || content.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM_EMPTY_RESPONSE",
                    "The AI service returned an empty answer. Try again.", true);
        }
        return content.trim();
    }
}
