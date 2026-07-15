package com.serviceos.integration.byd.infrastructure;

import com.serviceos.integration.byd.spi.BydCpimSubmitReviewGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * BYD V7.3.1 提审 HTTP 适配器。
 *
 * <p>本适配器故意不重试。协议没有幂等键和状态查询，任何 I/O 不确定性都必须上交 Delivery 内核。</p>
 */
@Component
final class HttpBydCpimSubmitReviewGateway implements BydCpimSubmitReviewGateway {
    static final String PATH = "/jumpto/openapi/sp/pushSubmitReviewInfo";
    private static final int MAXIMUM_RESPONSE_BYTES = 64 * 1024;

    private final String baseUrl;
    private final Duration requestTimeout;
    private final HttpClient client;

    HttpBydCpimSubmitReviewGateway(
            @Value("${serviceos.integration.byd.cpim.outbound-base-url:}") String baseUrl,
            @Value("${serviceos.integration.byd.cpim.connect-timeout:PT5S}") Duration connectTimeout,
            @Value("${serviceos.integration.byd.cpim.request-timeout:PT15S}") Duration requestTimeout
    ) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.requestTimeout = positive(requestTimeout, "requestTimeout");
        this.client = HttpClient.newBuilder()
                .connectTimeout(positive(connectTimeout, "connectTimeout"))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public Response send(Request request) throws TransportException {
        URI endpoint = endpoint();
        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("APP_KEY", request.appKey())
                .header("Nonce", request.nonce())
                .header("Cur_Time", request.currentDate())
                .header("Sign", request.signature())
                .POST(HttpRequest.BodyPublishers.ofByteArray(request.payload()))
                .build();
        try {
            HttpResponse<InputStream> response = client.send(
                    httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            byte[] body;
            // 不先将无界响应读入内存；超限后结果仍为 UNKNOWN，不得重发。
            try (InputStream input = response.body()) {
                body = input.readNBytes(MAXIMUM_RESPONSE_BYTES + 1);
            }
            if (body.length > MAXIMUM_RESPONSE_BYTES) {
                throw new TransportException(
                        TransportException.Kind.UNKNOWN, "BYD_RESPONSE_TOO_LARGE", null);
            }
            return new Response(response.statusCode(), body);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TransportException(TransportException.Kind.UNKNOWN, "BYD_REQUEST_INTERRUPTED", exception);
        } catch (IOException exception) {
            throw new TransportException(TransportException.Kind.UNKNOWN, "BYD_TRANSPORT_UNKNOWN", exception);
        }
    }

    private URI endpoint() throws TransportException {
        if (baseUrl.isBlank()) {
            throw new TransportException(TransportException.Kind.NOT_SENT, "BYD_ENDPOINT_NOT_CONFIGURED", null);
        }
        try {
            URI uri = URI.create(baseUrl.endsWith("/")
                    ? baseUrl.substring(0, baseUrl.length() - 1) + PATH
                    : baseUrl + PATH);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("unsupported scheme");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new TransportException(TransportException.Kind.NOT_SENT, "BYD_ENDPOINT_INVALID", exception);
        }
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
