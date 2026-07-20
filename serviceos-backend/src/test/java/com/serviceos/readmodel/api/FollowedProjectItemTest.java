package com.serviceos.readmodel.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FollowedProjectItemTest {
    @Test
    void withoutBadgesLeavesCountsNull() {
        UUID projectId = UUID.randomUUID();
        Instant followedAt = Instant.parse("2026-07-20T04:00:00Z");
        FollowedProjectItem item = FollowedProjectItem.withoutBadges(
                projectId,
                "演示项目",
                "PRJ-DEMO",
                "client-a",
                "ACTIVE",
                followedAt,
                "/projects/" + projectId);

        assertThat(item.projectId()).isEqualTo(projectId);
        assertThat(item.activeWorkOrderCount()).isNull();
        assertThat(item.openReviewCount()).isNull();
        assertThat(item.openCorrectionCount()).isNull();
        assertThat(item.slaBreachedCount()).isNull();
        assertThat(item.openTodoCount()).isNull();
        assertThat(item.activeWorkOrderCountTruncated()).isNull();
    }
}
