package com.serviceos.reliability.application;

import com.serviceos.reliability.spi.OutboxMessage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxWorkerTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-13T03:30:00Z"), ZoneOffset.UTC);

    @Test
    void publishesClaimedMessageAndMarksItWithTheSameEventId() {
        OutboxMessage message = message(1);
        FakeQueue queue = new FakeQueue(message);
        List<UUID> published = new ArrayList<>();
        OutboxWorker worker = new OutboxWorker(
                queue, event -> published.add(event.eventId()), CLOCK,
                "worker-1", Duration.ofSeconds(30), 8);

        assertThat(worker.runOnce()).isEqualTo(OutboxWorker.RunResult.PUBLISHED);
        assertThat(published).containsExactly(message.eventId());
        assertThat(queue.published).containsExactly(message.eventId());
        assertThat(queue.failed).isEmpty();
    }

    @Test
    void publicationFailureIsPersistedForBoundedRetry() {
        OutboxMessage message = message(2);
        FakeQueue queue = new FakeQueue(message);
        OutboxWorker worker = new OutboxWorker(
                queue, event -> { throw new IllegalStateException("broker unavailable"); }, CLOCK,
                "worker-2", Duration.ofSeconds(30), 8);

        assertThat(worker.runOnce()).isEqualTo(OutboxWorker.RunResult.FAILED);
        assertThat(queue.published).isEmpty();
        assertThat(queue.failed).containsExactly(message.eventId());
        assertThat(queue.lastErrorCode).isEqualTo("IllegalStateException");
    }

    @Test
    void emptyQueueDoesNotCallPublisher() {
        FakeQueue queue = new FakeQueue(null);
        List<UUID> published = new ArrayList<>();
        OutboxWorker worker = new OutboxWorker(
                queue, event -> published.add(event.eventId()), CLOCK,
                "worker-3", Duration.ofSeconds(30), 8);

        assertThat(worker.runOnce()).isEqualTo(OutboxWorker.RunResult.EMPTY);
        assertThat(published).isEmpty();
    }

    @Test
    void publishSucceededButStatusWriteFailedIsNotMisclassifiedAsPublishFailure() {
        OutboxMessage message = message(3);
        FakeQueue queue = new FakeQueue(message);
        queue.failMarkPublished = true;
        OutboxWorker worker = new OutboxWorker(
                queue, event -> { }, CLOCK,
                "worker-4", Duration.ofSeconds(30), 8);

        assertThatThrownBy(worker::runOnce)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("status write failed");
        assertThat(queue.failed).isEmpty();
    }

    private static OutboxMessage message(int attemptNo) {
        return new OutboxMessage(
                UUID.fromString("dcb71831-e298-4526-b8e2-9966f9ea27bd"),
                UUID.fromString("3f35fa89-3353-4ae3-8ca8-a2c413136523"),
                "project", "project.created", 1, "Project",
                "31a330c2-f5f2-4872-b28a-2c0fa6a719cf", 1,
                "tenant-a", "corr-1", "idem-1", "partition-1",
                "{}", "0".repeat(64), CLOCK.instant(), attemptNo);
    }

    private static final class FakeQueue implements OutboxQueue {
        private OutboxMessage next;
        private final List<UUID> published = new ArrayList<>();
        private final List<UUID> failed = new ArrayList<>();
        private String lastErrorCode;
        private boolean failMarkPublished;

        private FakeQueue(OutboxMessage next) {
            this.next = next;
        }

        @Override
        public Optional<OutboxMessage> claimNext(String workerId, Duration leaseDuration) {
            OutboxMessage claimed = next;
            next = null;
            return Optional.ofNullable(claimed);
        }

        @Override
        public void markPublished(OutboxMessage message, String workerId, Instant attemptStartedAt) {
            if (failMarkPublished) {
                throw new IllegalStateException("status write failed");
            }
            published.add(message.eventId());
        }

        @Override
        public void markFailed(
                OutboxMessage message,
                String workerId,
                Instant attemptStartedAt,
                String errorCode,
                int maxAttempts
        ) {
            failed.add(message.eventId());
            lastErrorCode = errorCode;
        }
    }
}
