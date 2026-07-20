package com.serviceos.identity.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 主体变更时间线条目：生命周期事实 / 审计 / 登录成功事件的统一投影。
 */
public record PrincipalChangeTimelineItem(
        String source,
        String eventCode,
        String summary,
        String actorId,
        String result,
        String correlationId,
        Long principalVersion,
        Instant occurredAt,
        UUID refId
) {
    public PrincipalChangeTimelineItem {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(eventCode, "eventCode");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(refId, "refId");
    }
}
