package com.serviceos.identity.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 主体变更时间线条目：生命周期 / 审计 / 登录 / 组织任职 / RoleGrant /
 * 网点任职 / 师傅服务关系 / 师傅档案 的统一投影。
 */
public record PrincipalChangeTimelineItem(
        String source,
        String eventCode,
        String summary,
        String actorId,
        String actorDisplayName,
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

    /** 兼容旧调用：无显示名。 */
    public static PrincipalChangeTimelineItem of(
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
        return new PrincipalChangeTimelineItem(
                source, eventCode, summary, actorId, null, result, correlationId,
                principalVersion, occurredAt, refId);
    }

    public PrincipalChangeTimelineItem withActorDisplayName(String displayName) {
        return new PrincipalChangeTimelineItem(
                source, eventCode, summary, actorId, displayName, result, correlationId,
                principalVersion, occurredAt, refId);
    }
}
