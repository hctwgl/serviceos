package com.serviceos.evidence.application;

import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.ClientCapabilityRuntimeGate;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.FrozenBundleClientCapabilityProbe;
import com.serviceos.dispatch.api.TechnicianActiveAssignmentQuery;
import com.serviceos.evidence.api.CorrectionCaseService;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.EvidenceCommandService;
import com.serviceos.evidence.api.EvidenceSetSnapshotService;
import com.serviceos.evidence.api.EvidenceSlotQueryService;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.TechnicianCorrectionView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
import com.serviceos.network.api.TechnicianProfileView;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.HandlingTaskContextQuery;
import com.serviceos.task.api.HandlingTaskContextView;
import com.serviceos.task.api.HumanTaskCommandService;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultTechnicianCorrectionServiceTest {
    private static final UUID PRINCIPAL = UUID.fromString("10000000-0000-4000-8000-000000000361");
    private static final UUID PROFILE = UUID.fromString("20000000-0000-4000-8000-000000000361");
    private static final UUID NETWORK = UUID.fromString("30000000-0000-4000-8000-000000000361");
    private static final UUID SOURCE_TASK = UUID.fromString("40000000-0000-4000-8000-000000000361");
    private static final UUID CORRECTION_TASK = UUID.fromString("50000000-0000-4000-8000-000000000361");
    private static final UUID CASE = UUID.fromString("60000000-0000-4000-8000-000000000361");
    private static final UUID BUNDLE = UUID.fromString("70000000-0000-4000-8000-000000000361");
    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");
    private static final String DIGEST = "a".repeat(64);

    private final PrincipalNetworkAffiliationQuery affiliations = mock(PrincipalNetworkAffiliationQuery.class);
    private final TechnicianActiveAssignmentQuery assignments = mock(TechnicianActiveAssignmentQuery.class);
    private final HandlingTaskContextQuery handlingTasks = mock(HandlingTaskContextQuery.class);
    private final HumanTaskCommandService humanTasks = mock(HumanTaskCommandService.class);
    private final CorrectionCaseRepository correctionRepository = mock(CorrectionCaseRepository.class);
    private final CorrectionCaseService corrections = mock(CorrectionCaseService.class);
    private final EvidenceSlotQueryService slots = mock(EvidenceSlotQueryService.class);
    private final EvidenceCommandService evidence = mock(EvidenceCommandService.class);
    private final EvidenceSetSnapshotService snapshots = mock(EvidenceSetSnapshotService.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final ClientCapabilityRuntimeGate runtimeGate = mock(ClientCapabilityRuntimeGate.class);
    private final FrozenBundleClientCapabilityProbe capabilityProbe = mock(FrozenBundleClientCapabilityProbe.class);
    private final ConfigurationService configurations = mock(ConfigurationService.class);
    private final TaskFulfillmentContextService sourceTasks = mock(TaskFulfillmentContextService.class);
    private DefaultTechnicianCorrectionService service;

    @BeforeEach
    void setUp() {
        service = new DefaultTechnicianCorrectionService(
                affiliations, assignments, handlingTasks, humanTasks, correctionRepository, corrections,
                slots, evidence, snapshots, authorization, runtimeGate, capabilityProbe, configurations,
                sourceTasks, Clock.fixed(NOW, ZoneOffset.UTC));
        when(affiliations.findActiveTechnicianProfile("tenant-361", PRINCIPAL))
                .thenReturn(Optional.of(new TechnicianProfileView(
                        PROFILE, PRINCIPAL, "师傅", "ACTIVE", 1,
                        Instant.EPOCH, Instant.EPOCH, null, null, null)));
        when(affiliations.listActiveTechnicianMemberships(eq("tenant-361"), eq(PROFILE), any()))
                .thenReturn(List.of(new NetworkTechnicianMembershipView(
                        UUID.randomUUID(), NETWORK, PROFILE, "ACTIVE", Instant.EPOCH, null,
                        "fixture", Instant.EPOCH, null, null, null, 1)));
        when(correctionRepository.find("tenant-361", CASE)).thenReturn(Optional.of(correction()));
        when(assignments.filterTaskIdsForNetwork("tenant-361", NETWORK.toString(), List.of(SOURCE_TASK)))
                .thenReturn(List.of(SOURCE_TASK));
        when(handlingTasks.findForActor(
                "tenant-361", CORRECTION_TASK, PRINCIPAL.toString(),
                CorrectionTaskAccessValidator.TASK_TYPE))
                .thenReturn(Optional.of(handlingTask()));
        when(sourceTasks.find("tenant-361", SOURCE_TASK)).thenReturn(Optional.of(sourceTask()));
        when(slots.listForTask(eq(principal()), any(), eq(SOURCE_TASK))).thenReturn(List.of(slot()));
        when(capabilityProbe.findIncompatibilityDetail(any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void listSlotsRejectsWhenRuntimeGateDenies() {
        doThrow(new BusinessProblem(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED, "师傅 iOS 不支持签名槽位"))
                .when(runtimeGate)
                .requireCompatibleEvidenceSlots(eq("TECHNICIAN_IOS"), any(), any(), any());

        assertThatThrownBy(() -> service.listSlots(
                principal(), "corr-361", "TECHNICIAN|NETWORK|" + NETWORK, "TECHNICIAN_IOS", CASE))
                .isInstanceOfSatisfying(BusinessProblem.class, problem ->
                        assertThat(problem.code()).isEqualTo(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED));
    }

    @Test
    void listSlotsInvokesGateForKnownTechnicianWeb() {
        service.listSlots(
                principal(), "corr-ok", "TECHNICIAN|NETWORK|" + NETWORK, "TECHNICIAN_WEB", CASE);
        verify(runtimeGate).requireCompatibleEvidenceSlots(
                eq("TECHNICIAN_WEB"), any(), any(), any());
    }

    @Test
    void listAnnotatesClientCapabilityUnsupportedDetailWithoutHidingItem() {
        when(handlingTasks.listForActor(
                "tenant-361", PRINCIPAL.toString(), CorrectionTaskAccessValidator.TASK_TYPE))
                .thenReturn(List.of(handlingTask("READY")));
        when(correctionRepository.findByCorrectionTaskId("tenant-361", CORRECTION_TASK))
                .thenReturn(Optional.of(correction()));
        when(capabilityProbe.findIncompatibilityDetail(
                eq("tenant-361"), eq("TECHNICIAN_IOS"), eq(BUNDLE), eq(DIGEST), eq("form.install")))
                .thenReturn(Optional.of("当前客户端（师傅 iOS）不支持本任务表单所需能力：form.widget.signature"));

        List<TechnicianCorrectionView> views = service.list(
                principal(), "corr-preflight", "TECHNICIAN|NETWORK|" + NETWORK, "TECHNICIAN_IOS");

        assertThat(views).hasSize(1);
        assertThat(views.getFirst().correctionCaseId()).isEqualTo(CASE);
        assertThat(views.getFirst().clientCapabilityUnsupportedDetail())
                .isEqualTo("当前客户端（师傅 iOS）不支持本任务表单所需能力：form.widget.signature");
    }

    @Test
    void listLeavesDetailNullWhenProbeSkips() {
        when(handlingTasks.listForActor(
                "tenant-361", PRINCIPAL.toString(), CorrectionTaskAccessValidator.TASK_TYPE))
                .thenReturn(List.of(handlingTask("READY")));
        when(correctionRepository.findByCorrectionTaskId("tenant-361", CORRECTION_TASK))
                .thenReturn(Optional.of(correction()));

        List<TechnicianCorrectionView> views = service.list(
                principal(), "corr-unknown", "TECHNICIAN|NETWORK|" + NETWORK, "UNKNOWN");

        assertThat(views).hasSize(1);
        assertThat(views.getFirst().clientCapabilityUnsupportedDetail()).isNull();
        verify(capabilityProbe).findIncompatibilityDetail(
                eq("tenant-361"), eq("UNKNOWN"), eq(BUNDLE), eq(DIGEST), eq("form.install"));
    }

    private static CurrentPrincipal principal() {
        return new CurrentPrincipal(PRINCIPAL.toString(), "tenant-361",
                CurrentPrincipal.PrincipalType.USER, "technician-correction", Set.of());
    }

    private static CorrectionCaseView correction() {
        return new CorrectionCaseView(
                CASE, UUID.randomUUID(), SOURCE_TASK, null, null, null, null,
                List.of("IMAGE.BLUR"), CORRECTION_TASK, "IN_PROGRESS", "tester", NOW,
                null, null, null, null, null, null, null, List.of());
    }

    private static HandlingTaskContextView handlingTask() {
        return handlingTask("RUNNING");
    }

    private static HandlingTaskContextView handlingTask(String status) {
        return new HandlingTaskContextView(
                CORRECTION_TASK, CorrectionTaskAccessValidator.TASK_TYPE, CASE.toString(),
                status, PRINCIPAL.toString(), 2, true, true);
    }

    private static TaskFulfillmentContext sourceTask() {
        return new TaskFulfillmentContext(
                SOURCE_TASK, UUID.randomUUID(), UUID.randomUUID(),
                BUNDLE, DIGEST, "INSTALL", "INSTALLATION", "HUMAN",
                "form.install", null, null, null, null, "COMPLETED", PROFILE.toString(), false, 1);
    }

    private static EvidenceSlotView slot() {
        UUID slotId = UUID.randomUUID();
        UUID resolutionId = UUID.randomUUID();
        return new EvidenceSlotView(
                slotId, resolutionId, SOURCE_TASK, UUID.randomUUID(),
                UUID.randomUUID(), "evidence", "1.0.0", "b".repeat(64),
                "sig", "sig", "签名", "SIGNATURE", true, 1, 1, null, null,
                "{\"mediaType\":\"SIGNATURE\"}", "d".repeat(64), "ACTIVE", NOW);
    }
}
