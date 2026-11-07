package com.researchassistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.researchassistant.exception.ApiException;
import com.researchassistant.support.FakeHttpServer;
import com.researchassistant.support.FakeHttpServer.Reply;

class LlmClientTest {

    private FakeHttpServer server;
    private LlmClient client;

    @BeforeEach
    void start() {
        server = new FakeHttpServer();
        client = new LlmClient(server.url(), "test-key", "test-model");
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private static String completion(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + quote(content) + "}}]}";
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private ApiException chatFails() {
        return assertThrows(ApiException.class, () -> client.chat("system", "user", 100));
    }

    @Test
    void returnsTheTrimmedAnswerText() {
        server.reply(Reply.json(200, completion("  Hello there \n")));
        assertThat(client.chat("system", "user", 100)).isEqualTo("Hello there");
    }

    @Test
    void sendsModelMessagesAndBearerKeyToTheChatEndpoint() {
        server.reply(Reply.json(200, completion("ok")));
        client.chat("be brief", "what is 2+2?", 123);
        assertThat(server.lastPath()).isEqualTo("/chat/completions");
        assertThat(server.lastAuthorization()).isEqualTo("Bearer test-key");
        assertThat(server.lastBody()).contains("\"model\":\"test-model\"").contains("be brief").contains("what is 2+2?")
                .contains("\"max_tokens\":123").contains("\"stream\":false");
    }

    @Test
    void trailingSlashInTheBaseUrlIsIgnored() {
        LlmClient slashed = new LlmClient(server.url() + "/", "test-key", "test-model");
        server.reply(Reply.json(200, completion("ok")));
        slashed.chat("s", "u", 10);
        assertThat(server.lastPath()).isEqualTo("/chat/completions");
    }

    @Test
    void missingConfigurationFailsFastWithoutAnyHttpCall() {
        for (LlmClient unconfigured : new LlmClient[] {
                new LlmClient("", "key", "model"), new LlmClient(server.url(), "", "model"), new LlmClient(server.url(), "key", "")}) {
            ApiException e = assertThrows(ApiException.class, () -> unconfigured.chat("s", "u", 10));
            assertThat(e.getCode()).isEqualTo("LLM_NOT_CONFIGURED");
            assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(e.isRetryable()).isFalse();
        }
        assertThat(server.requestCount()).isZero();
    }

    @Test
    void rejectedKeyIsNotRetryable() {
        for (int status : new int[] {401, 403}) {
            server.reply(Reply.json(status, "{}"));
            ApiException e = chatFails();
            assertThat(e.getCode()).isEqualTo("LLM_AUTH_FAILED");
            assertThat(e.isRetryable()).isFalse();
        }
    }

    @Test
    void httpRateLimitIsRetryable() {
        server.reply(Reply.json(429, "{}"));
        ApiException e = chatFails();
        assertThat(e.getCode()).isEqualTo("RATE_LIMITED");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(e.isRetryable()).isTrue();
    }

    @Test
    void jsonNullBodyIsTreatedAsTheProvidersRateLimit() {
        server.reply(Reply.json(200, "null"));
        ApiException e = chatFails();
        assertThat(e.getCode()).isEqualTo("RATE_LIMITED");
        assertThat(e.isRetryable()).isTrue();
    }

    @Test
    void serverErrorMentionsTheStatusCode() {
        server.reply(Reply.json(500, "{}"));
        ApiException e = chatFails();
        assertThat(e.getCode()).isEqualTo("LLM_UPSTREAM_FAILED");
        assertThat(e.getMessage()).contains("HTTP 500");
        assertThat(e.isRetryable()).isTrue();
    }

    @Test
    void emptyChoicesOrBlankContentIsAnEmptyResponseError() {
        server.reply(Reply.json(200, "{\"choices\":[]}"));
        assertThat(chatFails().getCode()).isEqualTo("LLM_EMPTY_RESPONSE");
        server.reply(Reply.json(200, completion("   ")));
        assertThat(chatFails().getCode()).isEqualTo("LLM_EMPTY_RESPONSE");
        server.reply(Reply.json(200, "{\"something\":\"else\"}"));
        assertThat(chatFails().getCode()).isEqualTo("LLM_EMPTY_RESPONSE");
    }

    @Test
    void unreachableServerIsReportedAsUnavailable() {
        LlmClient dead = new LlmClient("http://127.0.0.1:1", "test-key", "test-model");
        ApiException e = assertThrows(ApiException.class, () -> dead.chat("s", "u", 10));
        assertThat(e.getCode()).isEqualTo("LLM_UNAVAILABLE");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(e.isRetryable()).isTrue();
    }

    @Test
    void apiKeyIsNeverIncludedInErrorMessages() {
        for (int status : new int[] {401, 403, 429, 500, 503}) {
            // A hostile or careless provider echoes the key back in its error body.
            server.reply(Reply.json(status, "{\"error\":{\"message\":\"Incorrect API key provided: test-key\"}}"));
            ApiException e = chatFails();
            assertThat(e.getMessage()).as("HTTP %d message", status).doesNotContain("test-key");
            // Positive control: the key really was sent, so the check above is not passing for the wrong reason.
            assertThat(server.lastAuthorization()).isEqualTo("Bearer test-key");
        }
    }
}
