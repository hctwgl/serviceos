package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Admin 个人关注项目列表项。 */
public record FollowedProjectItem(
        UUID projectId,
        String displayRef,
        String projectCode,
        String clientId,
        String status,
        Instant followedAt,
        String deepLink
) {
    public FollowedProjectItem {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(displayRef, "displayRef");
        Objects.requireNonNull(followedAt, "followedAt");
        Objects.requireNonNull(deepLink, "deepLink");
    }
}
