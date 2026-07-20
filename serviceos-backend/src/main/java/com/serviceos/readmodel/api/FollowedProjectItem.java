package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Admin 个人关注项目列表项。
 *
 * <p>角标计数字段 soft-gate：缺对应读能力时为 null；有下一页时 {@code *Truncated=true}
 * 表示至少该数量。</p>
 */
public record FollowedProjectItem(
        UUID projectId,
        String displayRef,
        String projectCode,
        String clientId,
        String status,
        Instant followedAt,
        String deepLink,
        Integer activeWorkOrderCount,
        Boolean activeWorkOrderCountTruncated,
        Integer openReviewCount,
        Boolean openReviewCountTruncated,
        Integer openCorrectionCount,
        Boolean openCorrectionCountTruncated,
        Integer slaBreachedCount,
        Boolean slaBreachedCountTruncated,
        Integer openTodoCount
) {
    public FollowedProjectItem {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(displayRef, "displayRef");
        Objects.requireNonNull(followedAt, "followedAt");
        Objects.requireNonNull(deepLink, "deepLink");
    }

    /** 兼容旧构造：无角标。 */
    public static FollowedProjectItem withoutBadges(
            UUID projectId,
            String displayRef,
            String projectCode,
            String clientId,
            String status,
            Instant followedAt,
            String deepLink
    ) {
        return new FollowedProjectItem(
                projectId, displayRef, projectCode, clientId, status, followedAt, deepLink,
                null, null, null, null, null, null, null, null, null);
    }
}
