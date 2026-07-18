package com.serviceos.bootstrap;

import com.serviceos.shared.ClientMetadata;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** 客户端元数据必须保持低基数；不可信原文不得进入请求上下文、日志或 Trace。 */
class CorrelationContextFilterTest {
    private final CorrelationContextFilter filter = new CorrelationContextFilter();

    @Test
    void exposesValidatedClientMetadataDuringRequestAndCleansMdcAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader(ClientMetadata.KIND_HEADER, "TECHNICIAN_IOS");
        request.addHeader(ClientMetadata.VERSION_HEADER, "1.2.3+42");

        filter.doFilter(request, new MockHttpServletResponse(), (currentRequest, response) -> {
            assertThat(currentRequest.getAttribute(ClientMetadata.KIND_ATTRIBUTE)).isEqualTo("TECHNICIAN_IOS");
            assertThat(currentRequest.getAttribute(ClientMetadata.VERSION_ATTRIBUTE)).isEqualTo("1.2.3+42");
            assertThat(MDC.get("clientKind")).isEqualTo("TECHNICIAN_IOS");
            assertThat(MDC.get("clientVersion")).isEqualTo("1.2.3+42");
        });

        assertThat(MDC.get("clientKind")).isNull();
        assertThat(MDC.get("clientVersion")).isNull();
    }

    @Test
    void invalidOrPartialMetadataBecomesBoundedSentinelWithoutEchoingInput() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader(ClientMetadata.KIND_HEADER, "TECHNICIAN_IOS\nsecret");

        filter.doFilter(request, new MockHttpServletResponse(), (currentRequest, response) -> {
            assertThat(currentRequest.getAttribute(ClientMetadata.KIND_ATTRIBUTE)).isEqualTo("UNKNOWN");
            assertThat(currentRequest.getAttribute(ClientMetadata.VERSION_ATTRIBUTE)).isEqualTo("UNSPECIFIED");
            assertThat(MDC.get("clientKind")).isEqualTo("UNKNOWN");
        });
    }
}
