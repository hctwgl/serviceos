package com.serviceos.evidence.application;

import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.ClientCapabilityRuntimeGate;
import com.serviceos.dispatch.api.ActiveServiceResponsibility;
import com.serviceos.dispatch.api.ActiveServiceResponsibilityService;
import com.serviceos.evidence.api.BeginEvidenceUploadOnBehalfCommand;
import com.serviceos.evidence.api.CorrectionCaseService;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.EvidenceCommandService;
import com.serviceos.evidence.api.EvidenceSlotQueryService;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.EvidenceSetSnapshotService;
import com.serviceos.evidence.api.EvidenceUploadSessionView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkMembershipView;
import com.serviceos.network.api.NetworkPortalTechnicianQuery;
import com.serviceos.network.api.NetworkPortalTechnicianView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** M368：on-behalf 要求 NETWORK_WEB，并在能力不兼容时阻断领域写命令。 */
class DefaultNetworkPortalEvidenceServiceTest {
    private static final UUID PRINCIPAL = UUID.fromString("019f83d1-1111-7f8c-9505-36fe5c0e8801");
    private static final UUID NETWORK = UUID.fromString("019f83d1-2222-7f8c-9505-36fe5c0e8803");
    private static final UUID TASK = UUID.fromString("019f83d1-9999-7f8c-9505-36fe5c0e880d");
    private static final UUID SLOT = UUID.fromString("019f83d1-aaaa-7f8c-9505-36fe5c0e880e");
    private static final UUID PROJECT = UUID.fromString("019f83d1-8888-7f8c-9505-36fe5c0e880b");
    private static final UUID TECH = UUID.fromString("019f83d1-5555-7f8c-9505-36fe5c0e8806");
    private static final UUID TECH_PRINCIPAL = UUID.fromString("019f83d1-6666-7f8c-9505-36fe5c0e8807");
    private static final Instant NOW = Instant.parse("2026-07-20T01:00:00Z");

    private final PrincipalNetworkAffiliationQuery affiliations = mock(PrincipalNetworkAffiliationQuery.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final ActiveServiceResponsibilityService responsibilities =
            mock(ActiveServiceResponsibilityService.class);
    private final NetworkPortalTechnicianQuery technicians = mock(NetworkPortalTechnicianQuery.class);
    private final EvidenceCommandService evidence = mock(EvidenceCommandService.class);
    private final EvidenceSlotQueryService slots = mock(EvidenceSlotQueryService.class);
    private final EvidenceSetSnapshotService snapshots = mock(EvidenceSetSnapshotService.class);
    private final CorrectionCaseService corrections = mock(CorrectionCaseService.class);
    private final ClientCapabilityRuntimeGate runtimeGate = mock(ClientCapabilityRuntimeGate.class);
    private DefaultNetworkPortalEvidenceService service;

    @BeforeEach
    void setUp() {
        service = new DefaultNetworkPortalEvidenceService(
                affiliations, authorization, responsibilities, technicians, evidence, slots,
                snapshots, corrections, runtimeGate, Clock.fixed(NOW, ZoneOffset.UTC));
        when(affiliations.listActiveNetworkMemberships(eq("tenant-a"), eq(PRINCIPAL), any()))
                .thenReturn(List.of(new NetworkMembershipView(
                        UUID.randomUUID(), NETWORK, PRINCIPAL, "MEMBER", "ACTIVE", NOW, null,
                        "fixture", NOW, null, null, null, 1)));
        when(responsibilities.find("tenant-a", TASK)).thenReturn(Optional.of(
                new ActiveServiceResponsibility(TASK, NETWORK.toString(), TECH.toString())));
        when(technicians.listActiveTechnicians("tenant-a", NETWORK)).thenReturn(List.of(
                new NetworkPortalTechnicianView(
                        UUID.randomUUID(), TECH, TECH_PRINCIPAL, "师傅A", "ACTIVE", "ACTIVE",
                        NOW, null, 1L, List.of())));
        when(corrections.listForTask(eq(principal()), any(), eq(TASK))).thenReturn(List.of(
                new CorrectionCaseView(
                        UUID.randomUUID(), PROJECT, TASK, UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), "d".repeat(64), List.of("IMAGE.BLUR"), null, "OPEN",
                        PRINCIPAL.toString(), NOW, null,
                        null, null, null, null, null, null, List.of())));
        when(slots.listForTask(eq(principal()), any(), eq(TASK))).thenReturn(List.of(
                new EvidenceSlotView(
                        SLOT, UUID.randomUUID(), TASK, PROJECT, UUID.randomUUID(),
                        "survey.site", "1.0.0", "b".repeat(64), "site.photo", "default",
                        "现场照片", "PHOTO", true, 1, 2, "c".repeat(64), "{\"kind\":\"FIXED\"}",
                        "{\"mediaType\":\"PHOTO\"}", "d".repeat(64), "MISSING", NOW)));
    }

    @Test
    void beginRejectsNonNetworkWebWithoutCallingEvidence() {
        assertThatThrownBy(() -> service.beginUploadOnBehalf(
                principal(), metadata("m368-kind"), "NETWORK|NETWORK|" + NETWORK,
                "TECHNICIAN_WEB", TASK, SLOT, beginCommand()))
                .isInstanceOfSatisfying(BusinessProblem.class, problem ->
                        assertThat(problem.code()).isEqualTo(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED));
        verify(evidence, never()).beginUploadOnBehalf(any(), any(), any());
        verify(runtimeGate, never()).requireCompatibleEvidenceSlots(any(), any(), any(), any());
    }

    @Test
    void beginDelegatesWhenNetworkWebCompatible() {
        EvidenceUploadSessionView session = new EvidenceUploadSessionView(
                UUID.randomUUID(), UUID.randomUUID(), TASK, SLOT, null, "CREATED", "PUT",
                "https://upload.invalid", Map.of(), NOW, NOW.plusSeconds(60));
        when(evidence.beginUploadOnBehalf(eq(principal()), any(), any())).thenReturn(session);

        EvidenceUploadSessionView result = service.beginUploadOnBehalf(
                principal(), metadata("m368-ok"), "NETWORK|NETWORK|" + NETWORK,
                "NETWORK_WEB", TASK, SLOT, beginCommand());

        assertThat(result).isSameAs(session);
        verify(runtimeGate).requireCompatibleEvidenceSlots(
                eq("NETWORK_WEB"), eq(List.of("PHOTO")), any(), eq(List.of()));
        verify(evidence).beginUploadOnBehalf(eq(principal()), any(), any());
    }

    private BeginEvidenceUploadOnBehalfCommand beginCommand() {
        return new BeginEvidenceUploadOnBehalfCommand(
                TASK, SLOT, null, "site.png", "image/png", 12L, "a".repeat(64),
                "{\"captureSource\":\"CAMERA\",\"capturedAt\":\"2026-07-17T02:00:00Z\"}",
                TECH.toString(), "整改代补", null);
    }

    private static CurrentPrincipal principal() {
        return new CurrentPrincipal(
                PRINCIPAL.toString(), "tenant-a", CurrentPrincipal.PrincipalType.USER,
                "network-portal", Set.of());
    }

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, key);
    }
}
