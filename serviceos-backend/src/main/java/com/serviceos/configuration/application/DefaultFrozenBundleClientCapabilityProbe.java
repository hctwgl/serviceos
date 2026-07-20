package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ClientCapabilityRuntimeGate;
import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.EffectiveDispatchClientKinds;
import com.serviceos.configuration.api.FrozenBundleClientCapabilityProbe;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 冻结 Bundle FORM/EVIDENCE 预检。复用运行时门禁与定向目标（M357/M358）。
 *
 * <p>UNKNOWN/非强制端在此短路，避免 Feed 列表为每个任务无谓读 Bundle；
 * 与 {@link ClientCapabilityRuntimeGate} 的强制范围保持一致（含 M368 {@code NETWORK_WEB}）。</p>
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

    @Override
    public EffectiveDispatchClientKinds resolveDispatchTargetClientKinds(
            String tenantId,
            UUID configurationBundleId,
            String configurationBundleDigest,
            String formAssetKey
    ) {
        if (configurationBundleId == null
                || configurationBundleDigest == null
                || configurationBundleDigest.isBlank()) {
            return EffectiveDispatchClientKinds.unfiltered();
        }
        List<List<String>> directed = new ArrayList<>();
        if (formAssetKey != null && !formAssetKey.isBlank()) {
            configurations.listBundleAssets(
                            tenantId, configurationBundleId, configurationBundleDigest,
                            ConfigurationAssetType.FORM)
                    .stream()
                    .filter(asset -> formAssetKey.equals(asset.assetKey()))
                    .map(ConfigurationAssetDefinition::supportedClientKinds)
                    .filter(kinds -> kinds != null && !kinds.isEmpty())
                    .forEach(directed::add);
        }
        configurations.listBundleAssets(
                        tenantId, configurationBundleId, configurationBundleDigest,
                        ConfigurationAssetType.EVIDENCE)
                .stream()
                .map(ConfigurationAssetDefinition::supportedClientKinds)
                .filter(kinds -> kinds != null && !kinds.isEmpty())
                .forEach(directed::add);
        if (directed.isEmpty()) {
            return EffectiveDispatchClientKinds.unfiltered();
        }
        Set<String> intersection = new LinkedHashSet<>(directed.getFirst());
        for (int i = 1; i < directed.size(); i++) {
            intersection.retainAll(directed.get(i));
        }
        return EffectiveDispatchClientKinds.directed(List.copyOf(intersection));
    }

    private static boolean enforceable(String clientKind) {
        return ClientCapabilityCatalog.TECHNICIAN_WEB.equals(clientKind)
                || ClientCapabilityCatalog.TECHNICIAN_IOS.equals(clientKind)
                || ClientCapabilityCatalog.NETWORK_WEB.equals(clientKind);
    }
}
