package com.serviceos.readmodel.application;

import com.serviceos.network.api.ServiceNetworkCoverageView;
import com.serviceos.workorder.api.WorkOrderDirectoryHeader;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AssignCandidateDistanceEvaluatorTest {
    private static final UUID WO = UUID.fromString("019f9100-0001-7000-8000-000000000001");
    private static final UUID WO_OTHER = UUID.fromString("019f9100-0001-7000-8000-000000000002");
    private static final UUID NETWORK = UUID.fromString("019f9100-0001-7000-8000-000000000010");
    private static final Map<String, String> NAMES = Map.of(
            "370000", "山东省",
            "370200", "青岛市",
            "370203", "市北区");

    @Test
    void sameDistrictWhenCoverageHitsDistrict() {
        var projection = AssignCandidateDistanceEvaluator.evaluate(
                header(WO, "370000", "370200", "370203"),
                List.of(coverage("370203")),
                List.of(),
                NAMES);
        assertThat(projection.distanceTier()).isEqualTo("SAME_DISTRICT");
        assertThat(projection.coverageMatched()).isTrue();
        assertThat(projection.distanceSummary()).contains("同区").contains("市北区");
        assertThat(projection.workOrderRegionSummary()).isEqualTo("青岛市 · 市北区");
    }

    @Test
    void outsideCoverageWhenCoverageMisses() {
        var projection = AssignCandidateDistanceEvaluator.evaluate(
                header(WO, "370000", "370200", "370203"),
                List.of(coverage("370100")),
                List.of(),
                NAMES);
        assertThat(projection.distanceTier()).isEqualTo("OUTSIDE_COVERAGE");
        assertThat(projection.coverageMatched()).isFalse();
        assertThat(projection.distanceSummary()).contains("覆盖未命中");
    }

    @Test
    void unknownWhenWorkOrderHasNoRegion() {
        var projection = AssignCandidateDistanceEvaluator.evaluate(
                header(WO, null, null, null),
                List.of(coverage("370200")),
                List.of(),
                NAMES);
        assertThat(projection.distanceTier()).isEqualTo("UNKNOWN");
        assertThat(projection.distanceSummary()).contains("缺少行政区");
    }

    @Test
    void refinesWithOpenTaskSameDistrictProximity() {
        var projection = AssignCandidateDistanceEvaluator.evaluate(
                header(WO, "370000", "370200", "370203"),
                List.of(coverage("370000")),
                List.of(header(WO_OTHER, "370000", "370200", "370203")),
                NAMES);
        assertThat(projection.distanceTier()).isEqualTo("SAME_DISTRICT");
        assertThat(projection.distanceSummary()).contains("当前任务同区");
    }

    private static WorkOrderDirectoryHeader header(
            UUID id, String province, String city, String district
    ) {
        return new WorkOrderDirectoryHeader(
                id, "BYD_OCEAN", "HOME_CHARGING", province, city, district, Instant.parse("2026-07-21T00:00:00Z"));
    }

    private static ServiceNetworkCoverageView coverage(String regionCode) {
        return new ServiceNetworkCoverageView(
                UUID.randomUUID(), NETWORK, "BYD_OCEAN", "HOME_CHARGING", regionCode);
    }
}
