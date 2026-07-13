package com.serviceos.bootstrap;

import com.serviceos.shared.CorrelationIds;
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
        request.setAttribute(CorrelationIds.REQUEST_ATTRIBUTE, correlationId);
        response.setHeader(CorrelationIds.HEADER_NAME, correlationId);

        Span.current().setAttribute(BAGGAGE_FIELD, correlationId);
        Baggage baggage = Baggage.current().toBuilder().put(BAGGAGE_FIELD, correlationId).build();
        Context context = baggage.storeInContext(Context.current());
        try (Scope ignored = context.makeCurrent(); MDC.MDCCloseable ignoredMdc = MDC.putCloseable(
                "correlationId", correlationId)) {
            filterChain.doFilter(request, response);
        }
    }
}
