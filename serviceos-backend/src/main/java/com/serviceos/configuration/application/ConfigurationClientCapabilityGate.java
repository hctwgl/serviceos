package com.serviceos.configuration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceos.configuration.api.ClientCompatibilityReport;
import com.serviceos.configuration.api.ConfigurationAssetType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 配置发布客户端能力门禁。
 *
 * <p>默认（未声明 supportedClientKinds）：所需能力在全部生产师傅端并集中均不存在时失败关闭；
 * 分端缺口写入报告但不单独阻断。</p>
 *
 * <p>声明定向目标后：对声明集合的并集做阻断判断，且声明集合内每一端必须完全兼容（硬校验），
 * 使 H5-only 等定向发布成为显式治理而非偶然副作用。</p>
 */
@Component
final class ConfigurationClientCapabilityGate {
    private final ConfigurationClientCapabilityAnalyzer analyzer;

    ConfigurationClientCapabilityGate() {
        this(new ObjectMapper());
    }

    ConfigurationClientCapabilityGate(ObjectMapper objectMapper) {
        this.analyzer = new ConfigurationClientCapabilityAnalyzer(objectMapper);
    }

    ClientCompatibilityReport evaluate(ConfigurationAssetType assetType, String definitionJson) {
        return evaluate(assetType, definitionJson, null);
    }

    ClientCompatibilityReport evaluate(
            ConfigurationAssetType assetType,
            String definitionJson,
            List<String> supportedClientKinds
    ) {
        if (assetType != ConfigurationAssetType.FORM && assetType != ConfigurationAssetType.EVIDENCE) {
            return new ClientCompatibilityReport(List.of(), List.of(), List.of());
        }
        Set<String> required = analyzer.requiredCapabilities(assetType, definitionJson);
        List<String> targetKinds = resolveTargetKinds(supportedClientKinds);
        boolean directed = supportedClientKinds != null && !supportedClientKinds.isEmpty();

        Set<String> union = new LinkedHashSet<>();
        for (String kind : targetKinds) {
            union.addAll(ClientCapabilityCatalog.capabilitiesOf(kind));
        }

        List<String> blocking = new ArrayList<>();
        for (String code : required) {
            if (!union.contains(code)) {
                if (directed) {
                    blocking.add("客户端能力不兼容：定向目标客户端（"
                            + String.join("、", targetKinds)
                            + "）均不支持「"
                            + ClientCapabilityCodes.label(code) + "」（" + code + "），禁止发布");
                } else {
                    blocking.add("客户端能力不兼容：当前生产师傅端均不支持「"
                            + ClientCapabilityCodes.label(code) + "」（" + code + "），禁止发布");
                }
            }
        }

        List<ClientCompatibilityReport.ClientReport> reports = new ArrayList<>();
        for (String kind : targetKinds) {
            Set<String> supported = ClientCapabilityCatalog.capabilitiesOf(kind);
            List<String> missing = required.stream()
                    .filter(code -> !supported.contains(code))
                    .sorted()
                    .collect(Collectors.toCollection(ArrayList::new));
            List<String> notes = missing.stream()
                    .map(code -> ClientCapabilityCodes.label(code) + "（" + code + "）")
                    .toList();
            reports.add(new ClientCompatibilityReport.ClientReport(
                    kind, missing.isEmpty(), missing, notes));
            // 定向发布：声明端必须完全兼容，避免把不兼容端标进目标集合。
            if (directed && !missing.isEmpty()) {
                for (String code : missing) {
                    blocking.add("定向目标客户端 "
                            + kind + " 不支持「"
                            + ClientCapabilityCodes.label(code) + "」（" + code
                            + "）；请缩小 supportedClientKinds 或移除该能力");
                }
            }
        }

        return new ClientCompatibilityReport(
                required.stream().sorted().toList(),
                List.copyOf(blocking),
                List.copyOf(reports));
    }

    static List<String> resolveTargetKinds(List<String> supportedClientKinds) {
        if (supportedClientKinds == null || supportedClientKinds.isEmpty()) {
            return List.copyOf(ClientCapabilityCatalog.productionTechnicianKinds());
        }
        LinkedHashSet<String> kinds = new LinkedHashSet<>();
        for (String kind : supportedClientKinds) {
            if (kind == null || kind.isBlank()) {
                continue;
            }
            String normalized = kind.trim();
            if (!ClientCapabilityCatalog.productionTechnicianKinds().contains(normalized)) {
                throw new IllegalArgumentException(
                        "supportedClientKinds 仅允许 TECHNICIAN_WEB / TECHNICIAN_IOS，收到="
                                + normalized);
            }
            kinds.add(normalized);
        }
        if (kinds.isEmpty()) {
            throw new IllegalArgumentException("supportedClientKinds 不能为空数组");
        }
        return List.copyOf(kinds);
    }
}
