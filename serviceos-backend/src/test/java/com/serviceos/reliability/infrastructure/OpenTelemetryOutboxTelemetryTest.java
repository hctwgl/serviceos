package com.serviceos.reliability.infrastructure;

import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxTraceHeaders;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTelemetryOutboxTelemetryTest {
    @Test
    void restoresPersistedW3cParentAcrossTheAsyncBoundary() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(provider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        PrometheusMeterRegistry meters = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        OpenTelemetryOutboxTelemetry telemetry = new OpenTelemetryOutboxTelemetry(sdk, meters);
        Span apiSpan = sdk.getTracer("test").spanBuilder("POST /api/v1/projects").startSpan();

        OutboxTraceHeaders headers;
        try (Scope ignored = apiSpan.makeCurrent()) {
            headers = telemetry.capture();
        }
        OutboxMessage message = message(headers);
        String result = telemetry.observePublish(message, () -> "PUBLISHED");
        apiSpan.end();

        var spans = exporter.getFinishedSpanItems();
        var workerSpan = spans.stream().filter(span -> span.getName().equals("outbox publish"))
                .findFirst().orElseThrow();
        assertThat(result).isEqualTo("PUBLISHED");
        assertThat(headers.traceParent()).contains(apiSpan.getSpanContext().getTraceId());
        assertThat(workerSpan.getTraceId()).isEqualTo(apiSpan.getSpanContext().getTraceId());
        assertThat(workerSpan.getParentSpanId()).isEqualTo(apiSpan.getSpanContext().getSpanId());
        assertThat(workerSpan.getKind()).isEqualTo(SpanKind.CONSUMER);
        String traceAttributes = workerSpan.getAttributes().asMap().toString();
        assertThat(traceAttributes)
                .doesNotContain("13812345678", "LGXCE6CB1N0123456", "199.50", "Bearer secret")
                .doesNotContain("payload", "token", "phone", "address", "vin", "price", "amount");
        assertThat(meters.get("serviceos.outbox.publish.total").counter().count()).isEqualTo(1.0);
        assertThat(meters.scrape()).contains(
                "serviceos_outbox_publish_total{module=\"reliability\",result=\"published\"} 1.0");

        provider.close();
        meters.close();
    }

    private static OutboxMessage message(OutboxTraceHeaders headers) {
        return new OutboxMessage(
                UUID.fromString("dcb71831-e298-4526-b8e2-9966f9ea27bd"),
                UUID.fromString("3f35fa89-3353-4ae3-8ca8-a2c413136523"),
                "project", "project.created", 1, "Project",
                "31a330c2-f5f2-4872-b28a-2c0fa6a719cf", 1,
                "tenant-a", "corr-1", "idem-1", "partition-1",
                "{\"phone\":\"13812345678\",\"vin\":\"LGXCE6CB1N0123456\","
                        + "\"price\":199.50,\"authorization\":\"Bearer secret\"}",
                "0".repeat(64), Instant.parse("2026-07-13T03:30:00Z"), 1,
                headers.traceParent(), headers.traceState());
    }
}
