package com.serviceos.reliability.infrastructure;

import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalOutboxPublisherTest {
    @Test
    void dispatchesToEveryMatchingHandler() {
        List<String> calls = new ArrayList<>();
        var publisher = new LocalOutboxPublisher(List.of(
                handler("workorder.received", calls, "one"),
                handler("other", calls, "ignored"),
                handler("workorder.received", calls, "two")));

        publisher.publish(message("workorder.received"));

        assertThat(calls).containsExactly("one", "two");
    }

    @Test
    void failsClosedWhenNoLocalHandlerOwnsTheEvent() {
        var publisher = new LocalOutboxPublisher(List.of());
        assertThatThrownBy(() -> publisher.publish(message("workorder.received")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No local outbox handler");
        assertThatThrownBy(() -> publisher.publish(message("task.completed")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No local outbox handler");
    }

    @Test
    void notificationEventMayHaveNoLocalSubscriber() {
        new LocalOutboxPublisher(List.of()).publish(message("workflow.started"));
    }

    private static OutboxMessageHandler handler(String type, List<String> calls, String marker) {
        return new OutboxMessageHandler() {
            @Override
            public boolean supports(String eventType, int schemaVersion) {
                return type.equals(eventType) && schemaVersion == 1;
            }

            @Override
            public void handle(OutboxMessage message) {
                calls.add(marker);
            }
        };
    }

    private static OutboxMessage message(String type) {
        UUID id = UUID.randomUUID();
        return new OutboxMessage(id, id, "workorder", type, 1, "WorkOrder",
                UUID.randomUUID().toString(), 1, "tenant", "corr", "cause", "partition",
                "{}", "a".repeat(64), Instant.parse("2026-07-14T01:00:00Z"), 1);
    }
}
