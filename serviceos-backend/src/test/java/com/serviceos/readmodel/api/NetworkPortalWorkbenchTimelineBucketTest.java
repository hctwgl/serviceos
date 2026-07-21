package com.serviceos.readmodel.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetworkPortalWorkbenchTimelineBucketTest {
    @Test
    void rejectsNegativeCount() {
        var bucket = new NetworkPortalWorkbenchTimelineBucket(
                NetworkPortalWorkbenchTimelineBucket.UNASSIGNED, "待分配", 2, "待指派师傅任务 2 个");
        assertThat(bucket.count()).isEqualTo(2);
        assertThatThrownBy(() -> new NetworkPortalWorkbenchTimelineBucket(
                NetworkPortalWorkbenchTimelineBucket.UNASSIGNED, "待分配", -1, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
