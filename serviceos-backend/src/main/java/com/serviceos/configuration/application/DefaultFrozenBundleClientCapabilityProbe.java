package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ClientCapabilityRuntimeGate;
import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.FrozenBundleClientCapabilityProbe;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 冻结 Bundle FORM/EVIDENCE 预检。复用运行时门禁与定向目标（M357/M358）。
 *
 * <p>UNKNOWN/非师傅端在此短路，避免 Feed 列表为每个任务无谓读 Bundle；
 * 与 {@link ClientCapabilityRuntimeGate} 的强制范围保持一致。</p>
 */
@Service
final class DefaultFrozenBundleClientCapabilityProbe implements FrozenBundleClientCapabilityProbe {
    private final ConfigurationService configurations;
    private final ClientCapabilityRuntimeGate runtimeGate;

    DefaultFrozenBundleClientCapabilityProbe(
            ConfigurationService configurations,
            ClientCapabilityRuntimeGate runtimeGate
    ) {
        this.configurations = configurations;
        this.runtimeGate = runtimeGate;
    }

    @Override
    public void requireCompatible(
            String tenantId,
            String clientKind,
            UUID configurationBundleId,
            String configurationBundleDigest,
            String formAssetKey
    ) {
        findIncompatibilityDetail(
                tenantId, clientKind, configurationBundleId, configurationBundleDigest, formAssetKey)
                .ifPresent(detail -> {
                    throw new BusinessProblem(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED, detail);
                });
    }

    @Override
    public Optional<String> findIncompatibilityDetail(
            String tenantId,
            String clientKind,
            UUID configurationBundleId,
            String configurationBundleDigest,
            String formAssetKey
    ) {
        if (!enforceable(clientKind)
                || configurationBundleId == null
                || configurationBundleDigest == null
                || configurationBundleDigest.isBlank()) {
            return Optional.empty();
        }
        try {
            if (formAssetKey != null && !formAssetKey.isBlank()) {
                List<ConfigurationAssetDefinition> forms = configurations.listBundleAssets(
                                tenantId, configurationBundleId, configurationBundleDigest,
                                ConfigurationAssetType.FORM)
                        .stream()
                        .filter(asset -> formAssetKey.equals(asset.assetKey()))
                        .toList();
                for (ConfigurationAssetDefinition form : forms) {
                    runtimeGate.requireCompatible(
                            clientKind, ConfigurationAssetType.FORM, form.definitionJson(),
                            form.supportedClientKinds());
                }
            }
            List<ConfigurationAssetDefinition> evidenceAssets = configurations.listBundleAssets(
                    tenantId, configurationBundleId, configurationBundleDigest,
                    ConfigurationAssetType.EVIDENCE);
            for (ConfigurationAssetDefinition evidence : evidenceAssets) {
                runtimeGate.requireCompatible(
                        clientKind, ConfigurationAssetType.EVIDENCE, evidence.definitionJson(),
                        evidence.supportedClientKinds());
            }
            return Optional.empty();
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED) {
                return Optional.of(problem.getMessage());
            }
            throw problem;
        }
    }

    private static boolean enforceable(String clientKind) {
        return ClientCapabilityCatalog.TECHNICIAN_WEB.equals(clientKind)
                || ClientCapabilityCatalog.TECHNICIAN_IOS.equals(clientKind);
    }
}
