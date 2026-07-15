package com.serviceos.integration.byd.infrastructure;

import com.serviceos.integration.byd.spi.BydCpimSubmitReviewGateway;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpBydCpimSubmitReviewGatewayTest {
    @Test
    void sendsExactlyOneSignedRequestWithoutRedirectOrInternalRetry() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> signature = new AtomicReference<>();
        server.createContext(HttpBydCpimSubmitReviewGateway.PATH, exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            signature.set(exchange.getRequestHeaders().getFirst("Sign"));
            byte[] response = "{\"errno\":0,\"errmsg\":\"成功\",\"data\":null}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var gateway = new HttpBydCpimSubmitReviewGateway(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofSeconds(1), Duration.ofSeconds(2));
            byte[] payload = "{\"operatePerson\":\"reviewer-1\",\"orderCode\":\"ORDER-1\",\"commitDate\":\"2026-07-15 16:00:00\"}"
                    .getBytes(StandardCharsets.UTF_8);

            var response = gateway.send(new BydCpimSubmitReviewGateway.Request(
                    "app-key", "nonce-1", "2026-07-15", "a".repeat(64), payload));

            assertThat(response.httpStatus()).isEqualTo(200);
            assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("\"errno\":0");
            assertThat(body.get()).isEqualTo(new String(payload, StandardCharsets.UTF_8));
            assertThat(signature.get()).isEqualTo("a".repeat(64));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingEndpointIsClassifiedAsDefinitelyNotSent() {
        var gateway = new HttpBydCpimSubmitReviewGateway(
                "", Duration.ofSeconds(1), Duration.ofSeconds(2));

        assertThatThrownBy(() -> gateway.send(new BydCpimSubmitReviewGateway.Request(
                "app-key", "nonce-1", "2026-07-15", "a".repeat(64), "{}".getBytes())))
                .isInstanceOfSatisfying(BydCpimSubmitReviewGateway.TransportException.class,
                        exception -> {
                            assertThat(exception.kind()).isEqualTo(
                                    BydCpimSubmitReviewGateway.TransportException.Kind.NOT_SENT);
                            assertThat(exception.errorCode()).isEqualTo("BYD_ENDPOINT_NOT_CONFIGURED");
                        });
    }

    @Test
    void oversizedResponseIsBoundedAndClassifiedAsUnknown() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(HttpBydCpimSubmitReviewGateway.PATH, exchange -> {
            byte[] response = new byte[65 * 1024];
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var gateway = new HttpBydCpimSubmitReviewGateway(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofSeconds(1), Duration.ofSeconds(2));

            assertThatThrownBy(() -> gateway.send(new BydCpimSubmitReviewGateway.Request(
                    "app-key", "nonce-1", "2026-07-15", "a".repeat(64), "{}".getBytes())))
                    .isInstanceOfSatisfying(BydCpimSubmitReviewGateway.TransportException.class,
                            exception -> {
                                assertThat(exception.kind()).isEqualTo(
                                        BydCpimSubmitReviewGateway.TransportException.Kind.UNKNOWN);
                                assertThat(exception.errorCode()).isEqualTo("BYD_RESPONSE_TOO_LARGE");
                            });
        } finally {
            server.stop(0);
        }
    }
}
