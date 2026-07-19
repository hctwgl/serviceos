package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ClientCapabilityRuntimeGate;
import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultFrozenBundleClientCapabilityProbeTest {
    private final ConfigurationService configurations = mock(ConfigurationService.class);
    private final ClientCapabilityRuntimeGate runtimeGate = mock(ClientCapabilityRuntimeGate.class);
    private DefaultFrozenBundleClientCapabilityProbe probe;
    private final UUID bundleId = UUID.fromString("10000000-0000-4000-8000-000000000359");

    @BeforeEach
    void setUp() {
        probe = new DefaultFrozenBundleClientCapabilityProbe(configurations, runtimeGate);
    }

    @Test
    void unknownClientSkipsWithoutReadingBundle() {
        Optional<String> detail = probe.findIncompatibilityDetail(
                "tenant", "UNKNOWN", bundleId, "a".repeat(64), "survey");
        assertThat(detail).isEmpty();
        verify(configurations, never()).listBundleAssets(any(), any(), any(), any());
        verify(runtimeGate, never()).requireCompatible(any(), any(), any(), any());
    }

    @Test
    void iosRejectsWhenRuntimeGateDeniesForm() {
        ConfigurationAssetDefinition form = new ConfigurationAssetDefinition(
                UUID.randomUUID(), ConfigurationAssetType.FORM, "survey", "1.0.0", "1.0.0",
                "{\"formKey\":\"survey\"}", "b".repeat(64), List.of());
        when(configurations.listBundleAssets(eq("tenant"), eq(bundleId), any(), eq(ConfigurationAssetType.FORM)))
                .thenReturn(List.of(form));
        when(configurations.listBundleAssets(eq("tenant"), eq(bundleId), any(), eq(ConfigurationAssetType.EVIDENCE)))
                .thenReturn(List.of());
        doThrow(new BusinessProblem(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED, "师傅 iOS 不支持"))
                .when(runtimeGate)
                .requireCompatible(eq("TECHNICIAN_IOS"), eq(ConfigurationAssetType.FORM), any(), any());

        assertThatThrownBy(() -> probe.requireCompatible(
                "tenant", "TECHNICIAN_IOS", bundleId, "a".repeat(64), "survey"))
                .isInstanceOfSatisfying(BusinessProblem.class, problem -> {
                    assertThat(problem.code()).isEqualTo(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED);
                    assertThat(problem.getMessage()).contains("师傅 iOS");
                });
    }
}
