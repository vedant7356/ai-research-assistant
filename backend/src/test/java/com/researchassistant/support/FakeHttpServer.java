package com.researchassistant.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Tiny local HTTP server so tests can drive the real HTTP clients without touching the internet. */
public class FakeHttpServer implements AutoCloseable {

    public record Reply(int status, String contentType, byte[] body) {
        public static Reply json(int status, String json) {
            return new Reply(status, "application/json", json.getBytes(StandardCharsets.UTF_8));
        }

        public static Reply bytes(int status, String contentType, byte[] body) {
            return new Reply(status, contentType, body);
        }
    }

    private final HttpServer server;
    private volatile Function<String, Reply> handler = path -> Reply.json(404, "{}");
    private volatile String lastPath;
    private volatile String lastRawQuery;
    private volatile String lastBody;
    private volatile String lastAuthorization;
    private final AtomicInteger requests = new AtomicInteger();

    public FakeHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    private void handle(HttpExchange ex) throws IOException {
        lastPath = ex.getRequestURI().getPath();
        lastRawQuery = ex.getRequestURI().getRawQuery();
        lastAuthorization = ex.getRequestHeaders().getFirst("Authorization");
        lastBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.incrementAndGet();
        Reply r = handler.apply(lastPath);
        ex.getResponseHeaders().set("Content-Type", r.contentType());
        ex.sendResponseHeaders(r.status(), r.body().length == 0 ? -1 : r.body().length);
        if (r.body().length > 0) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(r.body());
            }
        }
        ex.close();
    }

    /** Always answer with this reply. */
    public void reply(Reply reply) {
        this.handler = path -> reply;
    }

    /** Answer depending on the request path. */
    public void on(Function<String, Reply> handler) {
        this.handler = handler;
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public String lastPath() { return lastPath; }
    public String lastRawQuery() { return lastRawQuery; }
    public String lastBody() { return lastBody; }
    public String lastAuthorization() { return lastAuthorization; }
    public int requestCount() { return requests.get(); }

    @Override
    public void close() {
        server.stop(0);
    }
}
