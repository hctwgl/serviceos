package com.serviceos.readmodel.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetworkPortalAssignCandidateItemTest {
    @Test
    void copiesWarningsAndRejectsNegativeCounts() {
        UUID id = UUID.randomUUID();
        var item = new NetworkPortalAssignCandidateItem(
                id, "张师傅", "ACTIVE", "ACTIVE", 1, 2, 0,
                "已通过资质 2 项", 1, "另有 1 个未完成预约", false,
                "SAME_CITY", "同城 · 青岛市；当前任务同城", true,
                7, 10, List.of("提示"), true);
        assertThat(item.warnings()).containsExactly("提示");
        assertThat(item.scheduleConflictSummary()).contains("未完成预约");
        assertThat(item.distanceTier()).isEqualTo("SAME_CITY");
        assertThat(item.distanceSummary()).contains("同城");
        assertThat(item.coverageMatched()).isTrue();
        assertThat(item.assignable()).isTrue();
        assertThatThrownBy(() -> new NetworkPortalAssignCandidateItem(
                id, "张师傅", "ACTIVE", "ACTIVE", -1, 0, 0, "x",
                0, "无近期预约", false,
                "UNKNOWN", "服务区域未知", false,
                null, null, List.of(), true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
