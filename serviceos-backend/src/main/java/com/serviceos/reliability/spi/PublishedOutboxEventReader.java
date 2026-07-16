package com.serviceos.reliability.spi;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 只读扫描已成功发布的 Outbox 事件，供投影重建使用。
 * 不暴露 claim/lease，也不能被当作实时投递通道。
 */
public interface PublishedOutboxEventReader {
    /**
     * 按 created_at/outbox_id 升序返回状态为 PUBLISHED、且 eventType 命中的事件。
     * after 为 null 时从最早开始；否则严格大于该游标。
     */
    List<PublishedOutboxEvent> scanPublished(
            Collection<String> eventTypes,
            UUID afterOutboxId,
            Instant afterCreatedAt,
            int limit
    );

    /** 按业务 eventId 读取已发布事件；缺失返回 empty，供 dead letter 重放失败关闭为 DISCARDED。 */
    Optional<PublishedOutboxEvent> findPublishedByEventId(UUID eventId);

    /** 已发布事件及其 Outbox 创建时间（重建游标）。 */
    record PublishedOutboxEvent(OutboxMessage message, Instant createdAt) {
    }
}
