package com.serviceos.bootstrap;

import com.serviceos.shared.CorrelationIds;
import com.serviceos.shared.ClientMetadata;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 在认证、业务处理和错误响应之前固化唯一 correlationId，并写入响应、MDC 与 OTel 上下文。
 */
final class CorrelationContextFilter extends OncePerRequestFilter {
    private static final String BAGGAGE_FIELD = "serviceos.correlation_id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = CorrelationIds.normalizeOrCreate(request.getHeader(CorrelationIds.HEADER_NAME));
        ClientMetadata.Normalized client = ClientMetadata.normalize(
                request.getHeader(ClientMetadata.KIND_HEADER), request.getHeader(ClientMetadata.VERSION_HEADER));
        request.setAttribute(CorrelationIds.REQUEST_ATTRIBUTE, correlationId);
        request.setAttribute(ClientMetadata.KIND_ATTRIBUTE, client.kind());
        request.setAttribute(ClientMetadata.VERSION_ATTRIBUTE, client.version());
        response.setHeader(CorrelationIds.HEADER_NAME, correlationId);

        Span.current().setAttribute(BAGGAGE_FIELD, correlationId);
        Span.current().setAttribute("serviceos.client.kind", client.kind());
        Span.current().setAttribute("serviceos.client.version", client.version());
        Baggage baggage = Baggage.current().toBuilder().put(BAGGAGE_FIELD, correlationId).build();
        Context context = baggage.storeInContext(Context.current());
        try (Scope ignored = context.makeCurrent();
             MDC.MDCCloseable ignoredCorrelation = MDC.putCloseable("correlationId", correlationId);
             MDC.MDCCloseable ignoredKind = MDC.putCloseable("clientKind", client.kind());
             MDC.MDCCloseable ignoredVersion = MDC.putCloseable("clientVersion", client.version())) {
            filterChain.doFilter(request, response);
        }
    }
}
