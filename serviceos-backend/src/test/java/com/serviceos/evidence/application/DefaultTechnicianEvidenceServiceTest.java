package com.serviceos.evidence.application;

import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.dispatch.api.TechnicianActiveAssignmentQuery;
import com.serviceos.evidence.api.BeginEvidenceUploadCommand;
import com.serviceos.evidence.api.EvidenceCommandService;
import com.serviceos.evidence.api.EvidenceSlotQueryService;
import com.serviceos.evidence.api.EvidenceSetSnapshotService;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.EvidenceUploadSessionView;
import com.serviceos.evidence.api.TechnicianBeginEvidenceUploadCommand;
import com.serviceos.evidence.api.TechnicianCompleteTaskCommand;
import com.serviceos.forms.api.FormSubmissionService;
import com.serviceos.forms.api.FormSubmissionView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
import com.serviceos.network.api.TechnicianProfileView;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import com.serviceos.task.api.CompleteHumanTaskCommand;
import com.serviceos.task.api.HumanTaskCommandReceipt;
import com.serviceos.task.api.HumanTaskCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultTechnicianEvidenceServiceTest {
    private static final UUID PRINCIPAL = UUID.fromString("10000000-0000-4000-8000-000000000264");
    private static final UUID PROFILE = UUID.fromString("20000000-0000-4000-8000-000000000264");
    private static final UUID NETWORK = UUID.fromString("30000000-0000-4000-8000-000000000264");
    private static final UUID TASK = UUID.fromString("40000000-0000-4000-8000-000000000264");
    private static final UUID PROJECT = UUID.fromString("50000000-0000-4000-8000-000000000264");
    private static final UUID SLOT = UUID.fromString("60000000-0000-4000-8000-000000000264");
    private static final Instant NOW = Instant.parse("2026-07-18T12:00:00Z");

    private final PrincipalNetworkAffiliationQuery affiliations = mock(PrincipalNetworkAffiliationQuery.class);
    private final TechnicianActiveAssignmentQuery assignments = mock(TechnicianActiveAssignmentQuery.class);
    private final TaskFulfillmentContextService tasks = mock(TaskFulfillmentContextService.class);
    private final EvidenceSlotQueryService slots = mock(EvidenceSlotQueryService.class);
    private final EvidenceCommandService evidence = mock(EvidenceCommandService.class);
    private final EvidenceSetSnapshotService snapshots = mock(EvidenceSetSnapshotService.class);
    private final FormSubmissionService formSubmissions = mock(FormSubmissionService.class);
    private final HumanTaskCommandService humanTasks = mock(HumanTaskCommandService.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private DefaultTechnicianEvidenceService service;

    @BeforeEach
    void setUp() {
        service = new DefaultTechnicianEvidenceService(
                affiliations, assignments, tasks, slots, evidence, snapshots, formSubmissions, humanTasks,
                authorization,
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        when(affiliations.findActiveTechnicianProfile("tenant-264", PRINCIPAL))
                .thenReturn(Optional.of(new TechnicianProfileView(
                        PROFILE, PRINCIPAL, "师傅", "ACTIVE", 1,
                        Instant.EPOCH, Instant.EPOCH, null, null, null)));
        when(affiliations.listActiveTechnicianMemberships(eq("tenant-264"), eq(PROFILE), any()))
                .thenReturn(List.of(new NetworkTechnicianMembershipView(
                        UUID.randomUUID(), NETWORK, PROFILE, "ACTIVE", Instant.EPOCH, null,
                        "fixture", Instant.EPOCH, null, null, null, 1)));
        when(tasks.find("tenant-264", TASK)).thenReturn(Optional.of(task(PROFILE.toString())));
        when(assignments.filterTaskIdsForNetwork("tenant-264", NETWORK.toString(), List.of(TASK)))
                .thenReturn(List.of(TASK));
    }

    @Test
    void onlineBeginBuildsMinimalCaptureMetadataAndDelegatesSecurityKernel() {
        EvidenceUploadSessionView session = new EvidenceUploadSessionView(
                UUID.randomUUID(), UUID.randomUUID(), TASK, SLOT, null, "CREATED", "PUT",
                "https://upload.invalid/once", Map.of("Content-Type", "image/jpeg"), NOW, NOW.plusSeconds(600));
        when(evidence.beginUpload(eq(principal()), any(), any())).thenReturn(session);

        assertThat(service.beginUpload(
                principal(), new CommandMetadata("corr-264", "begin-264"), context(),
                new TechnicianBeginEvidenceUploadCommand(
                        TASK, SLOT, null, "现场.jpg", "image/jpeg", 4,
                        "a".repeat(64), "CAMERA", NOW))).isEqualTo(session);

        ArgumentCaptor<BeginEvidenceUploadCommand> command = ArgumentCaptor.forClass(BeginEvidenceUploadCommand.class);
        verify(evidence).beginUpload(eq(principal()), any(), command.capture());
        assertThat(command.getValue().captureMetadataJson())
                .contains("\"captureSource\":\"CAMERA\"")
                .contains("\"offlineFlag\":false")
                .doesNotContain("uploadedBy", "locationVerified", "onBehalfOf");
    }

    @Test
    void forgedContextChangedResponsibilityAndOfflineSourceFailClosed() {
        UUID forged = UUID.fromString("70000000-0000-4000-8000-000000000264");
        assertThatThrownBy(() -> service.listItems(
                principal(), "corr-forged", "TECHNICIAN|NETWORK|" + forged, TASK))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));

        when(tasks.find("tenant-264", TASK)).thenReturn(Optional.of(task("another-technician")));
        assertThatThrownBy(() -> service.listSlots(principal(), "corr-changed", context(), TASK))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));

        when(tasks.find("tenant-264", TASK)).thenReturn(Optional.of(task(PROFILE.toString())));
        assertThatThrownBy(() -> service.beginUpload(
                principal(), new CommandMetadata("corr-offline", "begin-offline"), context(),
                new TechnicianBeginEvidenceUploadCommand(
                        TASK, SLOT, null, "offline.jpg", "image/jpeg", 4,
                        "b".repeat(64), "EXTERNAL", NOW)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    @Test
    void dualInputCompletionRebuildsTrustedRefsAndDigestsOnServer() {
        UUID snapshotId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        String snapshotDigest = "c".repeat(64);
        String formDigest = "d".repeat(64);
        TaskFulfillmentContext dualTask = task(PROFILE.toString(), "survey.form");
        when(tasks.find("tenant-264", TASK)).thenReturn(Optional.of(dualTask));
        when(snapshots.get(principal(), "corr-complete", snapshotId)).thenReturn(
                new EvidenceSetSnapshotView(snapshotId, TASK, PROJECT, UUID.randomUUID(),
                        "TASK_SUBMISSION", 1, snapshotDigest, "{}", PRINCIPAL.toString(), NOW, List.of()));
        when(formSubmissions.get(principal(), "corr-complete", submissionId)).thenReturn(
                new FormSubmissionView(submissionId, TASK, PROJECT, UUID.randomUUID(), "survey.form",
                        1, "{}", formDigest, "VALIDATED", List.of(), List.of(), null,
                        PRINCIPAL.toString(), NOW));
        when(humanTasks.complete(eq(principal()), any(), any())).thenReturn(
                new HumanTaskCommandReceipt(TASK, "COMPLETED", PRINCIPAL.toString(), 2, NOW));

        HumanTaskCommandReceipt receipt = service.completeTask(
                principal(), new CommandMetadata("corr-complete", "complete-265"), context(),
                new TechnicianCompleteTaskCommand(TASK, 1, snapshotId, submissionId));

        assertThat(receipt.status()).isEqualTo("COMPLETED");
        ArgumentCaptor<CompleteHumanTaskCommand> completion =
                ArgumentCaptor.forClass(CompleteHumanTaskCommand.class);
        verify(humanTasks).complete(eq(principal()), any(), completion.capture());
        assertThat(completion.getValue().resultRef()).isEqualTo("form-submission://" + submissionId);
        assertThat(completion.getValue().resultDigest()).isEqualTo(formDigest);
        assertThat(completion.getValue().inputVersionRefs())
                .extracting(ref -> ref.kind() + ":" + ref.digest())
                .containsExactlyInAnyOrder(
                        "FORM_SUBMISSION:" + formDigest,
                        "EVIDENCE_SET_SNAPSHOT:" + snapshotDigest);
    }

    private static TaskFulfillmentContext task(String responsible) {
        return task(responsible, null);
    }

    private static TaskFulfillmentContext task(String responsible, String formRef) {
        return new TaskFulfillmentContext(
                TASK, PROJECT, UUID.randomUUID(), UUID.randomUUID(), "bundle-digest",
                "SURVEY", "SURVEY", "HUMAN", formRef, "evidence-ref",
                "RUNNING", responsible, false, 1);
    }

    private static String context() {
        return "TECHNICIAN|NETWORK|" + NETWORK;
    }

    private static CurrentPrincipal principal() {
        return new CurrentPrincipal(PRINCIPAL.toString(), "tenant-264",
                CurrentPrincipal.PrincipalType.USER, "technician-evidence-test", Set.of());
    }
}
