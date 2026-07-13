package com.serviceos.reliability.infrastructure;

import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxTelemetry;
import com.serviceos.reliability.spi.OutboxTraceHeaders;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 使用 W3C Trace Context 串联 API 事务与异步发布，并只把有限结果值写入指标标签。
 */
@Component
final class OpenTelemetryOutboxTelemetry implements OutboxTelemetry {
    private static final String TRACE_PARENT = "traceparent";
    private static final String TRACE_STATE = "tracestate";
    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    };

    private final TextMapPropagator propagator;
    private final Tracer tracer;
    private final MeterRegistry meters;

    OpenTelemetryOutboxTelemetry(OpenTelemetry openTelemetry, MeterRegistry meters) {
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
        this.tracer = openTelemetry.getTracer("com.serviceos.reliability.outbox");
        this.meters = meters;
    }

    @Override
    public OutboxTraceHeaders capture() {
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(Context.current(), carrier, Map::put);
        return new OutboxTraceHeaders(carrier.get(TRACE_PARENT), carrier.get(TRACE_STATE));
    }

    @Override
    public <T> T observePublish(OutboxMessage message, Supplier<T> action) {
        Map<String, String> carrier = new HashMap<>();
        putIfPresent(carrier, TRACE_PARENT, message.traceParent());
        putIfPresent(carrier, TRACE_STATE, message.traceState());
        Context parent = propagator.extract(Context.root(), carrier, MAP_GETTER);
        Span span = tracer.spanBuilder("outbox publish")
                .setParent(parent)
                .setSpanKind(SpanKind.CONSUMER)
                // 这些标识只进入 Trace，不作为指标标签，避免无界基数。
                .setAttribute("serviceos.module", "reliability")
                .setAttribute("messaging.system", "serviceos-outbox")
                .setAttribute("messaging.operation.name", "publish")
                .setAttribute("messaging.message.id", message.eventId().toString())
                .setAttribute("serviceos.outbox.id", message.outboxId().toString())
                .setAttribute("serviceos.correlation_id", message.correlationId())
                .setAttribute("serviceos.event_type", message.eventType())
                .startSpan();
        Timer.Sample sample = Timer.start(meters);
        String result = "error";
        try (Scope ignored = span.makeCurrent();
             MDC.MDCCloseable ignoredCorrelation = MDC.putCloseable("correlationId", message.correlationId());
             MDC.MDCCloseable ignoredEvent = MDC.putCloseable("eventId", message.eventId().toString())) {
            T value = action.get();
            result = normalizeResult(value);
            if ("failed".equals(result)) {
                span.setStatus(StatusCode.ERROR, "publish_failed");
            } else {
                span.setStatus(StatusCode.OK);
            }
            return value;
        } catch (RuntimeException exception) {
            // 仅记录异常类型/栈，不把异常 message 作为属性，避免下游错误正文携带敏感数据。
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, exception.getClass().getSimpleName());
            throw exception;
        } finally {
            sample.stop(Timer.builder("serviceos.outbox.publish")
                    .description("Outbox publish duration by bounded result")
                    .tag("module", "reliability")
                    .tag("result", result)
                    .register(meters));
            meters.counter("serviceos.outbox.publish.total", "module", "reliability", "result", result)
                    .increment();
            span.setAttribute("serviceos.result", result);
            span.end();
        }
    }

    private static String normalizeResult(Object value) {
        if (value == null) {
            return "unknown";
        }
        String raw = value.toString();
        return switch (raw) {
            case "PUBLISHED" -> "published";
            case "FAILED" -> "failed";
            default -> "unknown";
        };
    }

    private static void putIfPresent(Map<String, String> carrier, String key, String value) {
        if (value != null && !value.isBlank()) {
            carrier.put(key, value);
        }
    }
}
