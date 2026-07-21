package com.serviceos.readmodel.application;

import com.serviceos.network.api.ServiceNetworkCoverageView;
import com.serviceos.workorder.api.WorkOrderDirectoryHeader;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Network 分配候选「距离」读模型：基于工单省市区与网点 Coverage 的行政区亲和，
 * 不以经纬度伪造路网距离（仓库尚无工单/师傅坐标权威事实）。
 */
final class AssignCandidateDistanceEvaluator {
    static final String TIER_SAME_DISTRICT = "SAME_DISTRICT";
    static final String TIER_SAME_CITY = "SAME_CITY";
    static final String TIER_SAME_PROVINCE = "SAME_PROVINCE";
    static final String TIER_OUTSIDE_COVERAGE = "OUTSIDE_COVERAGE";
    static final String TIER_UNKNOWN = "UNKNOWN";

    private AssignCandidateDistanceEvaluator() {
    }

    record DistanceProjection(
            String distanceTier,
            String distanceSummary,
            boolean coverageMatched,
            String workOrderRegionSummary
    ) {
        DistanceProjection {
            Objects.requireNonNull(distanceTier, "distanceTier");
            Objects.requireNonNull(distanceSummary, "distanceSummary");
        }
    }

    static DistanceProjection evaluate(
            WorkOrderDirectoryHeader workOrder,
            Collection<ServiceNetworkCoverageView> coverageRows,
            Collection<WorkOrderDirectoryHeader> technicianOpenTaskHeaders,
            Map<String, String> regionNames
    ) {
        String workOrderRegionSummary = formatWorkOrderRegion(workOrder, regionNames);
        if (workOrder == null || regionCodesOf(workOrder).isEmpty()) {
            return new DistanceProjection(
                    TIER_UNKNOWN,
                    "服务区域未知（工单缺少行政区）",
                    false,
                    workOrderRegionSummary);
        }

        Set<String> coverageCodes = new LinkedHashSet<>();
        if (coverageRows != null) {
            for (ServiceNetworkCoverageView row : coverageRows) {
                if (row.regionCode() != null && !row.regionCode().isBlank()) {
                    coverageCodes.add(row.regionCode().trim());
                }
            }
        }

        String coverageTier;
        boolean coverageMatched;
        if (coverageCodes.isEmpty()) {
            coverageTier = TIER_UNKNOWN;
            coverageMatched = false;
        } else {
            coverageTier = bestMatchTier(workOrder, coverageCodes);
            coverageMatched = !TIER_OUTSIDE_COVERAGE.equals(coverageTier)
                    && !TIER_UNKNOWN.equals(coverageTier);
        }

        String loadTier = bestOpenTaskProximity(workOrder, technicianOpenTaskHeaders);
        String distanceTier = refineTier(coverageTier, loadTier);
        String distanceSummary = buildSummary(
                distanceTier, coverageMatched, coverageTier, loadTier, workOrder, regionNames);
        return new DistanceProjection(
                distanceTier, distanceSummary, coverageMatched, workOrderRegionSummary);
    }

    static int tierRank(String tier) {
        return switch (tier == null ? TIER_UNKNOWN : tier) {
            case TIER_SAME_DISTRICT -> 0;
            case TIER_SAME_CITY -> 1;
            case TIER_SAME_PROVINCE -> 2;
            case TIER_UNKNOWN -> 3;
            case TIER_OUTSIDE_COVERAGE -> 4;
            default -> 5;
        };
    }

    static String formatWorkOrderRegion(
            WorkOrderDirectoryHeader workOrder, Map<String, String> regionNames
    ) {
        if (workOrder == null) {
            return null;
        }
        String district = label(workOrder.districtCode(), regionNames);
        String city = label(workOrder.cityCode(), regionNames);
        String province = label(workOrder.provinceCode(), regionNames);
        if (district != null && city != null) {
            return city + " · " + district;
        }
        if (district != null) {
            return district;
        }
        if (city != null && province != null && !city.equals(province)) {
            return province + " · " + city;
        }
        if (city != null) {
            return city;
        }
        return province;
    }

    private static String refineTier(String coverageTier, String loadTier) {
        if (TIER_OUTSIDE_COVERAGE.equals(coverageTier)) {
            return TIER_OUTSIDE_COVERAGE;
        }
        if (loadTier != null && tierRank(loadTier) < tierRank(coverageTier)) {
            return loadTier;
        }
        return coverageTier;
    }

    private static String buildSummary(
            String distanceTier,
            boolean coverageMatched,
            String coverageTier,
            String loadTier,
            WorkOrderDirectoryHeader workOrder,
            Map<String, String> regionNames
    ) {
        String regionLabel = preferredRegionLabel(workOrder, regionNames, distanceTier);
        String base = switch (distanceTier) {
            case TIER_SAME_DISTRICT -> "同区 · " + regionLabel;
            case TIER_SAME_CITY -> "同城 · " + regionLabel;
            case TIER_SAME_PROVINCE -> "同省覆盖 · " + regionLabel;
            case TIER_OUTSIDE_COVERAGE -> "覆盖未命中 · " + regionLabel;
            default -> "服务区域未知";
        };
        if (TIER_UNKNOWN.equals(coverageTier) && !TIER_UNKNOWN.equals(distanceTier)) {
            base = base + "（网点覆盖未配置）";
        } else if (!coverageMatched && TIER_OUTSIDE_COVERAGE.equals(coverageTier)
                && loadTier != null && !TIER_OUTSIDE_COVERAGE.equals(loadTier)
                && !TIER_UNKNOWN.equals(loadTier)) {
            base = "覆盖未命中 · " + regionLabel;
        }
        if (loadTier != null && !TIER_UNKNOWN.equals(loadTier)) {
            String loadHint = switch (loadTier) {
                case TIER_SAME_DISTRICT -> "当前任务同区";
                case TIER_SAME_CITY -> "当前任务同城";
                case TIER_SAME_PROVINCE -> "当前任务同省";
                default -> "当前任务跨区域";
            };
            if (!base.contains(loadHint)) {
                base = base + "；" + loadHint;
            }
        }
        return base;
    }

    private static String preferredRegionLabel(
            WorkOrderDirectoryHeader workOrder,
            Map<String, String> regionNames,
            String tier
    ) {
        if (workOrder == null) {
            return "未知区域";
        }
        return switch (tier) {
            case TIER_SAME_DISTRICT -> firstNonBlank(
                    label(workOrder.districtCode(), regionNames),
                    label(workOrder.cityCode(), regionNames),
                    label(workOrder.provinceCode(), regionNames),
                    "目标行政区");
            case TIER_SAME_CITY -> firstNonBlank(
                    label(workOrder.cityCode(), regionNames),
                    label(workOrder.provinceCode(), regionNames),
                    "目标城市");
            default -> firstNonBlank(
                    label(workOrder.cityCode(), regionNames),
                    label(workOrder.provinceCode(), regionNames),
                    label(workOrder.districtCode(), regionNames),
                    "目标区域");
        };
    }

    private static String bestOpenTaskProximity(
            WorkOrderDirectoryHeader target,
            Collection<WorkOrderDirectoryHeader> openHeaders
    ) {
        if (openHeaders == null || openHeaders.isEmpty()) {
            return null;
        }
        String best = null;
        for (WorkOrderDirectoryHeader header : openHeaders) {
            if (header == null || header.workOrderId().equals(target.workOrderId())) {
                continue;
            }
            String tier = proximityBetween(target, header);
            if (best == null || tierRank(tier) < tierRank(best)) {
                best = tier;
            }
        }
        return best;
    }

    private static String proximityBetween(
            WorkOrderDirectoryHeader target, WorkOrderDirectoryHeader other
    ) {
        if (nonBlank(target.districtCode())
                && target.districtCode().trim().equalsIgnoreCase(safe(other.districtCode()))) {
            return TIER_SAME_DISTRICT;
        }
        if (nonBlank(target.cityCode())
                && target.cityCode().trim().equalsIgnoreCase(safe(other.cityCode()))) {
            return TIER_SAME_CITY;
        }
        if (nonBlank(target.provinceCode())
                && target.provinceCode().trim().equalsIgnoreCase(safe(other.provinceCode()))) {
            return TIER_SAME_PROVINCE;
        }
        return TIER_OUTSIDE_COVERAGE;
    }

    private static String bestMatchTier(WorkOrderDirectoryHeader workOrder, Set<String> coverageCodes) {
        Set<String> normalized = normalize(coverageCodes);
        if (nonBlank(workOrder.districtCode())
                && normalized.contains(workOrder.districtCode().trim().toLowerCase(Locale.ROOT))) {
            return TIER_SAME_DISTRICT;
        }
        if (nonBlank(workOrder.cityCode())
                && normalized.contains(workOrder.cityCode().trim().toLowerCase(Locale.ROOT))) {
            return TIER_SAME_CITY;
        }
        if (nonBlank(workOrder.provinceCode())
                && normalized.contains(workOrder.provinceCode().trim().toLowerCase(Locale.ROOT))) {
            return TIER_SAME_PROVINCE;
        }
        return TIER_OUTSIDE_COVERAGE;
    }

    private static Set<String> regionCodesOf(WorkOrderDirectoryHeader header) {
        Set<String> codes = new LinkedHashSet<>();
        if (nonBlank(header.provinceCode())) {
            codes.add(header.provinceCode().trim());
        }
        if (nonBlank(header.cityCode())) {
            codes.add(header.cityCode().trim());
        }
        if (nonBlank(header.districtCode())) {
            codes.add(header.districtCode().trim());
        }
        return codes;
    }

    private static Set<String> normalize(Set<String> codes) {
        Set<String> out = new LinkedHashSet<>();
        for (String code : codes) {
            if (code != null && !code.isBlank()) {
                out.add(code.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static String label(String code, Map<String, String> regionNames) {
        if (!nonBlank(code)) {
            return null;
        }
        String trimmed = code.trim();
        if (regionNames != null) {
            String name = regionNames.get(trimmed);
            if (name != null && !name.isBlank()) {
                return name.trim();
            }
        }
        return trimmed;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (nonBlank(value)) {
                return value;
            }
        }
        return "未知区域";
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
