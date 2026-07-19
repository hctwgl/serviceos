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
 * <p>阻断语义：所需能力在全部生产师傅端能力并集中均不存在时失败关闭。
 * 分端缺口写入报告但不单独阻断（灰度通道与 iOS 共用执行器未交付前避免误伤 H5 已验收能力）。</p>
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
        if (assetType != ConfigurationAssetType.FORM && assetType != ConfigurationAssetType.EVIDENCE) {
            return new ClientCompatibilityReport(List.of(), List.of(), List.of());
        }
        Set<String> required = analyzer.requiredCapabilities(assetType, definitionJson);
        Set<String> union = new LinkedHashSet<>();
        for (String kind : ClientCapabilityCatalog.productionTechnicianKinds()) {
            union.addAll(ClientCapabilityCatalog.capabilitiesOf(kind));
        }

        List<String> blocking = new ArrayList<>();
        for (String code : required) {
            if (!union.contains(code)) {
                blocking.add("客户端能力不兼容：当前生产师傅端均不支持「"
                        + ClientCapabilityCodes.label(code) + "」（" + code + "），禁止发布");
            }
        }

        List<ClientCompatibilityReport.ClientReport> reports = new ArrayList<>();
        for (String kind : ClientCapabilityCatalog.productionTechnicianKinds()) {
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
        }

        return new ClientCompatibilityReport(
                required.stream().sorted().toList(),
                List.copyOf(blocking),
                List.copyOf(reports));
    }
}
