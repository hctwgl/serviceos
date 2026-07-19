package com.serviceos.evidence.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.evidence.api.BeginEvidenceUploadCommand;
import com.serviceos.evidence.api.BeginCorrectionEvidenceUploadCommand;
import com.serviceos.evidence.api.CloseCorrectionCaseCommand;
import com.serviceos.evidence.api.CorrectionCaseQueryService;
import com.serviceos.evidence.api.CorrectionCaseQueueQuery;
import com.serviceos.evidence.api.CorrectionCaseService;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.CreateEvidenceSetSnapshotCommand;
import com.serviceos.evidence.api.CreateReviewCaseCommand;
import com.serviceos.evidence.api.DecideReviewCaseCommand;
import com.serviceos.evidence.api.ReviewTargetDecisionCommand;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.EvidenceCommandService;
import com.serviceos.evidence.api.EvidenceSetSnapshotService;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.FinalizeEvidenceUploadCommand;
import com.serviceos.evidence.api.FinalizeCorrectionEvidenceUploadCommand;
import com.serviceos.evidence.api.ResubmitCorrectionCaseCommand;
import com.serviceos.evidence.api.ReviewCaseService;
import com.serviceos.evidence.api.ReviewCaseView;
import com.serviceos.evidence.api.WaiveCorrectionCaseCommand;
import com.serviceos.files.infrastructure.LocalObjectTransferService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.task.application.TaskExecutionWorker;
import com.serviceos.task.api.ClaimHumanTaskCommand;
import com.serviceos.task.api.HumanTaskCommandService;
import com.serviceos.task.api.StartHumanTaskCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CorrectionCasePostgresIT {
    private static final String TENANT = "tenant-correction-case-it";
    private static final String TECHNICIAN = "technician-evidence-045";
    private static final String REVIEWER = "reviewer-evidence-045";
    private static final String WAIVER = "waiver-evidence-051";
    private static final Path STORAGE_ROOT = temporaryStorageRoot();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("serviceos")
            .withUsername("serviceos_test")
            .withPassword("serviceos_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("serviceos.files.local.root", STORAGE_ROOT::toString);
        registry.add("serviceos.files.local.signing-key",
                () -> "evidence-correction-it-signing-key-with-thirty-two-b");
        registry.add("serviceos.task.scheduling-enabled", () -> "false");
    }

    @Autowired ConfigurationService configurations;
    @Autowired EvidenceCommandService evidence;
    @Autowired EvidenceSetSnapshotService snapshots;
    @Autowired ReviewCaseService reviews;
    @Autowired CorrectionCaseService corrections;
    @Autowired CorrectionCaseQueryService correctionQueries;
    @Autowired HumanTaskCommandService humanTasks;
    @Autowired LocalObjectTransferService transfers;
    @Autowired TaskExecutionWorker worker;
    @Autowired List<OutboxMessageHandler> handlers;
    @Autowired JdbcClient jdbc;

    UUID projectId;
    UUID slotId;
    UUID taskId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("""
                TRUNCATE TABLE
                    evd_correction_resubmission, evd_correction_case, evd_correction_command_result,
                    evd_review_decision, evd_review_case, evd_review_command_result,
                    evd_evidence_set_member, evd_evidence_set_snapshot,
                    evd_evidence_validation, evd_evidence_command_result, evd_evidence_revision,
                    evd_evidence_item, evd_evidence_upload_session, evd_evidence_slot,
                    evd_task_evidence_resolution,
                    fil_download_authorization, fil_scan_result, fil_stored_file, fil_upload_session,
                    tsk_task_execution_guard, tsk_task_assignment, tsk_task,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project,
                    aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_inbox_record, rel_idempotency_record,
                    auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        deleteRecursively(STORAGE_ROOT);
        Files.createDirectories(STORAGE_ROOT);
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, project_status, aggregate_version, created_at)
                VALUES (:projectId, :tenantId, 'EVD-COR-IT', 'BYD', '资料整改测试项目',
                    :startsOn, 'ACTIVE', 1, now())
                """).param("projectId", projectId).param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1)).update();
        grant(TECHNICIAN, "evidence.submit", "evidence.read", "file.upload", "file.download",
                "task.claim", "task.start");
        grant(REVIEWER, "evidence.review", "evidence.read");
        grant(WAIVER, "evidence.waiveCorrection", "evidence.read");
        seedResolvedSlot();
    }

    @Test
    void rejectOpensCorrectionThenResubmitAndClose() throws Exception {
        EvidenceSetSnapshotView first = createSnapshot("first");
        completeSourceTask(first);
        ReviewCaseView review = reviews.create(reviewer(), metadata("review-create"),
                new CreateReviewCaseCommand(first.evidenceSetSnapshotId(), null));
        var rejectedResult = reviews.decide(reviewer(), metadata("review-reject"),
                decideCommand(review.reviewCaseId(), "REJECTED", List.of("IMAGE.BLUR"), "blurry"));
        ReviewCaseView rejected = rejectedResult.reviewCase();
        assertThat(rejected.status()).isEqualTo("REJECTED");

        UUID correctionId = jdbc.sql("""
                SELECT correction_case_id FROM evd_correction_case
                 WHERE source_review_case_id = :review
                """).param("review", review.reviewCaseId()).query(UUID.class).single();
        CorrectionCaseView opened = corrections.get(reviewer(), "corr-get", correctionId);
        assertThat(opened.status()).isEqualTo("IN_PROGRESS");
        assertThat(opened.correctionTaskId()).isNotNull();
        assertThat(opened.reasonCodes()).containsExactly("IMAGE.BLUR");
        assertThat(jdbc.sql("""
                SELECT task_type, task_kind, business_key, status
                  FROM tsk_task WHERE task_id = :task
                """).param("task", opened.correctionTaskId()).query().singleRow())
                .satisfies(row -> {
                    assertThat(row.get("task_type")).isEqualTo("evidence.correction");
                    assertThat(row.get("task_kind")).isEqualTo("HUMAN");
                    assertThat(row.get("business_key")).isEqualTo(correctionId.toString());
                    assertThat(row.get("status")).isEqualTo("READY");
                });
        assertThat(jdbc.sql("""
                SELECT principal_id, source_type, source_id
                  FROM tsk_task_assignment
                 WHERE task_id = :task AND assignment_kind = 'CANDIDATE' AND status = 'ACTIVE'
                """).param("task", opened.correctionTaskId()).query().singleRow())
                .satisfies(row -> {
                    assertThat(row.get("principal_id")).isEqualTo(TECHNICIAN);
                    assertThat(row.get("source_type")).isEqualTo("SYSTEM");
                    assertThat(row.get("source_id")).isEqualTo("CORRECTION_AUTO_CANDIDATE");
                });
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type='evidence.correction-case-created'")
                .query(Long.class).single()).isOne();

        var claimed = humanTasks.claim(technician(), metadata("correction-claim"),
                new ClaimHumanTaskCommand(opened.correctionTaskId(), 1));
        var started = humanTasks.start(technician(), metadata("correction-start"),
                new StartHumanTaskCommand(opened.correctionTaskId(), claimed.version()));
        assertThat(started.status()).isEqualTo("RUNNING");

        assertThatThrownBy(() -> corrections.close(reviewer(), metadata("close-too-early"),
                new CloseCorrectionCaseCommand(correctionId, "early")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.CORRECTION_CASE_STATE_CONFLICT));

        EvidenceSetSnapshotView second = createCorrectionSnapshot(opened, "second");
        CorrectionCaseView resubmitted = corrections.resubmit(technician(), metadata("resubmit-1"),
                new ResubmitCorrectionCaseCommand(correctionId, second.evidenceSetSnapshotId()));
        CorrectionCaseView resubmitReplay = corrections.resubmit(technician(), metadata("resubmit-1"),
                new ResubmitCorrectionCaseCommand(correctionId, second.evidenceSetSnapshotId()));
        assertThat(resubmitReplay.correctionCaseId()).isEqualTo(correctionId);
        assertThat(resubmitted.status()).isEqualTo("RESUBMITTED");
        assertThat(resubmitted.resubmissions()).hasSize(1);
        assertThat(resubmitted.latestResubmissionSnapshotId()).isEqualTo(second.evidenceSetSnapshotId());

        // resubmit 只产生新审核候选，不结束整改 Task；同一责任人可在下一轮继续补传。
        assertThat(jdbc.sql("SELECT status FROM tsk_task WHERE task_id=:task")
                .param("task", opened.correctionTaskId()).query(String.class).single()).isEqualTo("RUNNING");
        EvidenceSetSnapshotView third = createCorrectionSnapshot(opened, "third");
        CorrectionCaseView secondRound = corrections.resubmit(technician(), metadata("resubmit-2"),
                new ResubmitCorrectionCaseCommand(correctionId, third.evidenceSetSnapshotId()));
        assertThat(secondRound.status()).isEqualTo("RESUBMITTED");
        assertThat(secondRound.resubmissions()).hasSize(2);
        assertThat(secondRound.latestResubmissionSnapshotId()).isEqualTo(third.evidenceSetSnapshotId());
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type='evidence.correction-resubmitted'")
                .query(Long.class).single()).isEqualTo(2);

        CorrectionCaseView closed = corrections.close(reviewer(), metadata("close-1"),
                new CloseCorrectionCaseCommand(correctionId, "verified close"));
        CorrectionCaseView closeReplay = corrections.close(reviewer(), metadata("close-1"),
                new CloseCorrectionCaseCommand(correctionId, "verified close"));
        assertThat(closeReplay.status()).isEqualTo("CLOSED");
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedBy()).isEqualTo(REVIEWER);
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type='evidence.correction-closed'")
                .query(Long.class).single()).isOne();
        assertThat(jdbc.sql("""
                SELECT status, result_ref, result_digest FROM tsk_task WHERE task_id=:task
                """).param("task", opened.correctionTaskId()).query().singleRow())
                .satisfies(row -> {
                    assertThat(row.get("status")).isEqualTo("COMPLETED");
                    assertThat(row.get("result_ref")).isEqualTo("correction-case://" + correctionId);
                    assertThat(row.get("result_digest")).isEqualTo(third.contentDigest());
                });
        assertThat(jdbc.sql("""
                SELECT count(*) FROM tsk_task_assignment
                 WHERE task_id=:task AND status='ACTIVE'
                """).param("task", opened.correctionTaskId()).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type='task.handling-completed'")
                .query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT status FROM tsk_task WHERE task_id=:task")
                .param("task", taskId).query(String.class).single()).isEqualTo("COMPLETED");

        assertThatThrownBy(() -> corrections.resubmit(technician(), metadata("resubmit-after-close"),
                new ResubmitCorrectionCaseCommand(correctionId, second.evidenceSetSnapshotId())))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.CORRECTION_CASE_STATE_CONFLICT));
    }

    @Test
    void waiveMarksTerminalAndCancelsCorrectionTask() throws Exception {
        EvidenceSetSnapshotView first = createSnapshot("waive-first");
        completeSourceTask(first);
        ReviewCaseView review = reviews.create(reviewer(), metadata("waive-review-create"),
                new CreateReviewCaseCommand(first.evidenceSetSnapshotId(), null));
        reviews.decide(reviewer(), metadata("waive-review-reject"),
                decideCommand(review.reviewCaseId(), "REJECTED", List.of("IMAGE.BLUR"), "blurry"));
        UUID correctionId = jdbc.sql("""
                SELECT correction_case_id FROM evd_correction_case
                 WHERE source_review_case_id = :review
                """).param("review", review.reviewCaseId()).query(UUID.class).single();
        CorrectionCaseView opened = corrections.get(reviewer(), "waive-get", correctionId);
        assertThat(opened.status()).isEqualTo("IN_PROGRESS");
        UUID correctionTaskId = opened.correctionTaskId();
        assertThat(correctionTaskId).isNotNull();

        assertThatThrownBy(() -> corrections.waive(reviewer(), metadata("waive-denied"),
                new WaiveCorrectionCaseCommand(correctionId, "skip", "APR-1")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));

        assertThatThrownBy(() -> corrections.waive(waiver(), metadata("waive-empty"),
                new WaiveCorrectionCaseCommand(correctionId, " ", "APR-1")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));

        assertThatThrownBy(() -> corrections.waive(waiver(), metadata("waive-no-ref"),
                new WaiveCorrectionCaseCommand(correctionId, "authorized skip", null)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));

        CorrectionCaseView waived = corrections.waive(waiver(), metadata("waive-ok"),
                new WaiveCorrectionCaseCommand(correctionId, "authorized skip", "APR-WAIVE-1"));
        CorrectionCaseView replay = corrections.waive(waiver(), metadata("waive-ok"),
                new WaiveCorrectionCaseCommand(correctionId, "authorized skip", "APR-WAIVE-1"));
        assertThat(replay.status()).isEqualTo("WAIVED");
        assertThat(waived.status()).isEqualTo("WAIVED");
        assertThat(waived.waivedBy()).isEqualTo(WAIVER);
        assertThat(waived.waiveApprovalRef()).isEqualTo("APR-WAIVE-1");
        assertThat(waived.waiveNote()).isEqualTo("authorized skip");
        assertThat(waived.closedBy()).isNull();
        assertThat(jdbc.sql("""
                SELECT status FROM tsk_task WHERE task_id = :task
                """).param("task", correctionTaskId).query(String.class).single())
                .isEqualTo("CANCELLED");
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type='evidence.correction-waived'")
                .query(Long.class).single()).isOne();

        assertThatThrownBy(() -> corrections.resubmit(technician(), metadata("resubmit-after-waive"),
                new ResubmitCorrectionCaseCommand(correctionId, first.evidenceSetSnapshotId())))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.CORRECTION_CASE_STATE_CONFLICT));
        assertThatThrownBy(() -> corrections.close(reviewer(), metadata("close-after-waive"),
                new CloseCorrectionCaseCommand(correctionId, "nope")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.CORRECTION_CASE_STATE_CONFLICT));
        assertThatThrownBy(() -> corrections.waive(waiver(), metadata("waive-again"),
                new WaiveCorrectionCaseCommand(correctionId, "again", "APR-2")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.CORRECTION_CASE_STATE_CONFLICT));
    }

    @Test
    void approveDoesNotCreateCorrectionCase() throws Exception {
        EvidenceSetSnapshotView snapshot = createSnapshot("approve-only");
        ReviewCaseView review = reviews.create(reviewer(), metadata("approve-create"),
                new CreateReviewCaseCommand(snapshot.evidenceSetSnapshotId(), null));
        reviews.decide(reviewer(), metadata("approve-decide"),
                decideCommand(review.reviewCaseId(), "APPROVED", List.of(), "ok"));
        assertThat(jdbc.sql("SELECT count(*) FROM evd_correction_case").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type='evidence.correction-case-created'")
                .query(Long.class).single()).isZero();
    }

    @Test
    void authorizedCorrectionQueueDefaultsOpenFiltersAndUsesScopeBoundCursor() throws Exception {
        UUID firstCorrectionId = openCorrection("queue-1");
        UUID secondCorrectionId = openCorrection("queue-2");
        UUID firstReviewId = jdbc.sql("""
                SELECT source_review_case_id FROM evd_correction_case
                 WHERE correction_case_id = :id
                """).param("id", firstCorrectionId).query(UUID.class).single();

        var defaultOpen = correctionQueries.list(
                reviewer(), "corr-correction-queue-open",
                new CorrectionCaseQueueQuery(projectId, null, null, null, null, 20));
        assertThat(defaultOpen.items()).isEmpty();

        var firstPage = correctionQueries.list(
                reviewer(), "corr-correction-queue-1",
                new CorrectionCaseQueueQuery(projectId, "IN_PROGRESS", null, null, null, 1));
        assertThat(firstPage.items()).hasSize(1);
        assertThat(firstPage.nextCursor()).isNotBlank();
        var secondPage = correctionQueries.list(
                reviewer(), "corr-correction-queue-2",
                new CorrectionCaseQueueQuery(
                        projectId, "IN_PROGRESS", null, null, firstPage.nextCursor(), 1));
        assertThat(secondPage.items()).hasSize(1);
        assertThat(List.of(
                firstPage.items().getFirst().correctionCaseId(),
                secondPage.items().getFirst().correctionCaseId()))
                .containsExactlyInAnyOrder(firstCorrectionId, secondCorrectionId);
        assertThat(secondPage.items().getFirst().resubmissionCount()).isZero();
        assertThat(secondPage.toString()).doesNotContain(
                "sourceSnapshotContentDigest", "createdBy", "closedBy", "waivedBy",
                "waiveApprovalRef", "waiveNote");

        assertThat(correctionQueries.list(
                reviewer(), "corr-correction-queue-scope",
                new CorrectionCaseQueueQuery(null, "IN_PROGRESS", null, null, null, 20)).items())
                .extracting(item -> item.correctionCaseId())
                .containsExactlyInAnyOrder(firstCorrectionId, secondCorrectionId);
        assertThat(correctionQueries.list(
                reviewer(), "corr-correction-queue-source",
                new CorrectionCaseQueueQuery(
                        projectId, "IN_PROGRESS", null, firstReviewId, null, 20)).items())
                .extracting(item -> item.correctionCaseId())
                .containsExactly(firstCorrectionId);

        assertThatThrownBy(() -> correctionQueries.list(
                reviewer(), "corr-correction-queue-cursor",
                new CorrectionCaseQueueQuery(
                        projectId, "CLOSED", null, null, firstPage.nextCursor(), 1)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> correctionQueries.list(
                reviewer(), "corr-correction-queue-status",
                new CorrectionCaseQueueQuery(projectId, "UNKNOWN", null, null, null, 20)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    private UUID openCorrection(String marker) throws Exception {
        EvidenceSetSnapshotView snapshot = createSnapshot(marker);
        ReviewCaseView review = reviews.create(reviewer(), metadata("queue-review-" + marker),
                new CreateReviewCaseCommand(snapshot.evidenceSetSnapshotId(), null));
        reviews.decide(reviewer(), metadata("queue-reject-" + marker),
                decideCommand(review.reviewCaseId(), "REJECTED", List.of("IMAGE.BLUR"), "blurry"));
        return jdbc.sql("""
                SELECT correction_case_id FROM evd_correction_case
                 WHERE source_review_case_id = :review
                """).param("review", review.reviewCaseId()).query(UUID.class).single();
    }

    private EvidenceSetSnapshotView createSnapshot(String marker) throws Exception {
        UUID revisionId = uploadScanAndValidate(pngBytes(marker), "begin-" + marker, "cmd-" + marker);
        return snapshots.create(technician(), metadata("snap-" + marker),
                new CreateEvidenceSetSnapshotCommand(taskId, "TASK_SUBMISSION", List.of(revisionId)));
    }

    private void completeSourceTask(EvidenceSetSnapshotView snapshot) {
        // M265 提交先把源业务 Task 终态化并撤销活动分派；随后审核拒绝仍应从历史责任链
        // 精确派生整改候选人，而不是重开源 Task 或恢复旧分派。
        jdbc.sql("""
                UPDATE tsk_task
                   SET status='COMPLETED', completed_at=now(), version=version+1,
                       result_ref=:resultRef, result_digest=:resultDigest
                 WHERE task_id=:task
                """).param("task", taskId)
                .param("resultRef", "evidence-set-snapshot://" + snapshot.evidenceSetSnapshotId())
                .param("resultDigest", snapshot.contentDigest()).update();
        jdbc.sql("""
                UPDATE tsk_task_assignment
                   SET status='EXPIRED', effective_to=now(), revoked_by=:actor,
                       revoke_reason_code='TASK_COMPLETED'
                 WHERE task_id=:task AND status='ACTIVE'
                """).param("task", taskId).param("actor", TECHNICIAN).update();
    }

    private EvidenceSetSnapshotView createCorrectionSnapshot(CorrectionCaseView correction, String marker)
            throws Exception {
        byte[] content = pngBytes(marker);
        String checksum = sha256(content);
        Long itemCount = jdbc.sql("""
                SELECT count(*) FROM evd_evidence_item
                 WHERE tenant_id=:tenant AND task_id=:task AND slot_id=:slot
                """).param("tenant", TENANT).param("task", taskId).param("slot", slotId)
                .query(Long.class).single();
        UUID evidenceItemId = itemCount >= 2 ? jdbc.sql("""
                SELECT evidence_item_id FROM evd_evidence_item
                 WHERE tenant_id=:tenant AND task_id=:task AND slot_id=:slot
                 ORDER BY item_ordinal DESC LIMIT 1
                """).param("tenant", TENANT).param("task", taskId).param("slot", slotId)
                .query(UUID.class).single() : null;
        var session = evidence.beginCorrectionUpload(technician(), metadata("begin-" + marker),
                new BeginCorrectionEvidenceUploadCommand(
                        correction.correctionCaseId(), correction.correctionTaskId(), taskId, slotId,
                        evidenceItemId, "site-correction.png", "image/png", content.length, checksum,
                        "CAMERA", Instant.parse("2026-07-18T12:00:00Z")));
        transfers.upload(token(session.uploadUrl()), "image/png", content.length,
                new ByteArrayInputStream(content));
        var item = evidence.finalizeCorrectionUpload(technician(), metadata("fin-" + marker),
                new FinalizeCorrectionEvidenceUploadCommand(
                        correction.correctionCaseId(), correction.correctionTaskId(), taskId, slotId,
                        session.uploadSessionId(), checksum, "cmd-" + marker));
        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        handlers.stream().filter(handler -> handler.supports("file.scan-completed", 1))
                .forEach(handler -> handler.handle(latestScanCompletedEvent()));
        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        UUID revisionId = item.revisions().getFirst().evidenceRevisionId();
        return snapshots.createForCorrection(technician(), metadata("snap-" + marker),
                correction.correctionCaseId(), correction.correctionTaskId(), taskId, List.of(revisionId));
    }

    private UUID uploadScanAndValidate(byte[] content, String beginKey, String commandId)
            throws Exception {
        String checksum = sha256(content);
        var session = evidence.beginUpload(technician(), metadata(beginKey),
                new BeginEvidenceUploadCommand(
                        taskId, slotId, null, "site.png", "image/png",
                        content.length, checksum, captureJson()));
        transfers.upload(token(session.uploadUrl()), "image/png", content.length,
                new ByteArrayInputStream(content));
        var item = evidence.finalizeUpload(technician(), metadata("fin-" + beginKey),
                new FinalizeEvidenceUploadCommand(
                        taskId, slotId, session.uploadSessionId(), checksum, commandId));
        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        handlers.stream().filter(handler -> handler.supports("file.scan-completed", 1))
                .forEach(handler -> handler.handle(latestScanCompletedEvent()));
        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        return item.revisions().getFirst().evidenceRevisionId();
    }

    private void seedResolvedSlot() {
        String definition = """
                {"templateKey":"survey.site","version":"1.0.0","stage":"SURVEY",
                 "items":[{"evidenceKey":"site.photo","name":"现场照片","mediaType":"PHOTO","required":true,
                   "capture":{"minCount":1,"maxCount":2}}]}
                """;
        UUID assetId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.EVIDENCE, "survey.site", "1.0.0", "1.0.0",
                definition.trim(), Sha256.digest(definition.trim()))).versionId();
        ConfigurationBundleReference bundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        TENANT, projectId, "EVD-COR-BUNDLE", "1.0.0", "BYD", "HOME",
                        null, Instant.now().minusSeconds(60), null, List.of(assetId)));
        taskId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO tsk_task (
                    task_id, tenant_id, task_type, task_kind, business_key, payload_digest,
                    priority, status, next_run_at, attempt_count, max_attempts,
                    correlation_id, version, created_at, updated_at, claimed_by, claimed_at, started_at,
                    project_id, work_order_id, workflow_instance_id, stage_instance_id,
                    workflow_node_instance_id, workflow_node_id, workflow_definition_version_id,
                    workflow_definition_digest, configuration_bundle_id, configuration_bundle_digest,
                    stage_code)
                VALUES (:task, :tenant, 'SITE_SURVEY', 'HUMAN', :businessKey, :digest,
                    100, 'RUNNING', now(), 0, 1, 'corr-evd-045', 3, now(), now(),
                    :actor, now(), now(), :project, :workOrder, :workflow, :stage,
                    :nodeInstance, 'SITE_SURVEY', :definitionId, :digest, :bundle, :bundleDigest,
                    'SURVEY')
                """).param("task", taskId).param("tenant", TENANT).param("businessKey", taskId.toString())
                .param("digest", "d".repeat(64)).param("actor", TECHNICIAN)
                .param("project", projectId).param("workOrder", UUID.randomUUID())
                .param("workflow", UUID.randomUUID()).param("stage", UUID.randomUUID())
                .param("nodeInstance", UUID.randomUUID()).param("definitionId", assetId)
                .param("bundle", bundle.bundleId()).param("bundleDigest", bundle.manifestDigest())
                .update();
        jdbc.sql("""
                INSERT INTO tsk_task_assignment (
                    task_assignment_id, tenant_id, task_id, assignment_kind, principal_type,
                    principal_id, status, source_type, source_id, effective_from, created_by, created_at)
                VALUES (:id, :tenant, :task, 'RESPONSIBLE', 'USER', :actor, 'ACTIVE',
                    'MANUAL', 'M45-FIXTURE', now(), 'fixture', now())
                """).param("id", UUID.randomUUID()).param("tenant", TENANT).param("task", taskId)
                .param("actor", TECHNICIAN).update();

        UUID resolutionId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        String itemDefinition = """
                {"evidenceKey":"site.photo","name":"现场照片","mediaType":"PHOTO","required":true,
                 "capture":{"minCount":1,"maxCount":2}}
                """;
        jdbc.sql("""
                INSERT INTO evd_task_evidence_resolution (
                    resolution_id, tenant_id, project_id, task_id, configuration_bundle_id,
                    configuration_bundle_digest, stage_code, source_event_id, source_event_digest,
                    resolver_version, condition_input_digest, resolution_explanation,
                    generation_no, condition_fact_type, condition_fact_ref, condition_fact_revision,
                    slot_count, resolved_at)
                VALUES (:id, :tenant, :project, :task, :bundle, :digest, 'SURVEY', :event,
                    :eventDigest, 'FIXED_EVIDENCE_V1',
                    '44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a',
                    CAST('{"kind":"TEST_FIXED_CONTEXT","resolverVersion":"FIXED_EVIDENCE_V1"}' AS jsonb),
                    1, 'TASK_CREATED', CAST(:event AS varchar), 0, 1, now())
                """).param("id", resolutionId).param("tenant", TENANT).param("project", projectId)
                .param("task", taskId).param("bundle", bundle.bundleId())
                .param("digest", bundle.manifestDigest()).param("event", UUID.randomUUID())
                .param("eventDigest", "e".repeat(64)).update();
        jdbc.sql("""
                INSERT INTO evd_evidence_slot (
                    slot_id, tenant_id, project_id, task_id, resolution_id, template_version_id,
                    template_key, template_version, template_digest, requirement_code, occurrence_key,
                    requirement_name, media_type, required_flag, min_count, max_count,
                    condition_input_digest, resolution_explanation, requirement_definition,
                    requirement_digest, status_projection, resolved_at, slot_generation)
                VALUES (:slot, :tenant, :project, :task, :resolution, :template,
                    'survey.site', '1.0.0', :templateDigest, 'site.photo', 'default',
                    '现场照片', 'PHOTO', true, 1, 2, :conditionDigest,
                    CAST('{"kind":"FIXED"}' AS jsonb), CAST(:definition AS jsonb),
                    :reqDigest, 'MISSING', now(), 1)
                """).param("slot", slotId).param("tenant", TENANT).param("project", projectId)
                .param("task", taskId).param("resolution", resolutionId).param("template", assetId)
                .param("templateDigest", Sha256.digest(definition.trim()))
                .param("conditionDigest", "f".repeat(64))
                .param("definition", itemDefinition.trim())
                .param("reqDigest", Sha256.digest(itemDefinition.trim())).update();
        jdbc.sql("""
                INSERT INTO evd_evidence_resolution_member (
                    member_id, tenant_id, project_id, task_id, resolution_id, template_version_id,
                    requirement_code, occurrence_key, condition_result, active_slot_id,
                    previous_slot_id, transition, required_disposition, counting_item_count,
                    condition_input_digest, resolution_explanation, created_at)
                VALUES (:slot, :tenant, :project, :task, :resolution, :template,
                    'site.photo', 'default', true, :slot, NULL, 'ACTIVATED', 'NONE', 0,
                    :conditionDigest, CAST(:definition AS jsonb), now())
                """).param("slot", slotId).param("tenant", TENANT).param("project", projectId)
                .param("task", taskId).param("resolution", resolutionId).param("template", assetId)
                .param("conditionDigest", "f".repeat(64)).param("definition", itemDefinition.trim()).update();
    }

    private OutboxMessage latestScanCompletedEvent() {
        Map<String, Object> row = jdbc.sql("""
                SELECT outbox_id, event_id, module_name, event_type, schema_version,
                       aggregate_type, aggregate_id, aggregate_version, tenant_id,
                       correlation_id, causation_id, partition_key, payload::text AS payload,
                       payload_digest, occurred_at
                  FROM rel_outbox_event WHERE event_type='file.scan-completed'
                 ORDER BY occurred_at DESC LIMIT 1
                """).query().singleRow();
        return new OutboxMessage(
                uuid(row, "outbox_id"), uuid(row, "event_id"),
                text(row, "module_name"), text(row, "event_type"),
                number(row, "schema_version").intValue(),
                text(row, "aggregate_type"), text(row, "aggregate_id"),
                number(row, "aggregate_version").longValue(),
                text(row, "tenant_id"), text(row, "correlation_id"),
                text(row, "causation_id"), text(row, "partition_key"),
                text(row, "payload"), text(row, "payload_digest"),
                instant(row.get("occurred_at")), 1);
    }

    private void grant(String principalId, String... capabilities) {
        UUID role = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:role, :tenant, :code, '资料整改角色', 'ACTIVE', now())
                """).param("role", role).param("tenant", TENANT)
                .param("code", "correction-role-" + role).update();
        for (String capability : capabilities) {
            jdbc.sql("INSERT INTO auth_role_capability (role_id, capability_code, granted_at) VALUES (:role,:cap,now())")
                    .param("role", role).param("cap", capability).update();
        }
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at)
                VALUES (:grant, :tenant, :principal, :role, 'PROJECT', :project,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M45-CORRECTION', now())
                """).param("grant", UUID.randomUUID()).param("tenant", TENANT)
                .param("principal", principalId).param("role", role)
                .param("project", projectId.toString()).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at)
                VALUES (:grant, :tenant, :principal, :role, 'TENANT', :tenant,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M45-CORRECTION', now())
                """).param("grant", UUID.randomUUID()).param("tenant", TENANT)
                .param("principal", principalId).param("role", role).update();
    }

    private CurrentPrincipal technician() {
        return new CurrentPrincipal(
                TECHNICIAN, TENANT, CurrentPrincipal.PrincipalType.USER, "mobile", Set.of());
    }

    private CurrentPrincipal reviewer() {
        return new CurrentPrincipal(
                REVIEWER, TENANT, CurrentPrincipal.PrincipalType.USER, "ops-web", Set.of());
    }

    private CurrentPrincipal waiver() {
        return new CurrentPrincipal(
                WAIVER, TENANT, CurrentPrincipal.PrincipalType.USER, "ops-web", Set.of());
    }

    private static CommandMetadata metadata(String suffix) {
        return new CommandMetadata("corr-" + suffix, "idem-" + suffix);
    }

    private static String captureJson() {
        return "{\"captureSource\":\"CAMERA\",\"capturedAt\":\"2026-07-14T08:00:00Z\",\"deviceId\":\"DEV-1\"}";
    }

    private static String token(String url) {
        String path = URI.create(url).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static byte[] pngBytes(String marker) {
        byte[] prefix = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] body = marker.getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[prefix.length + body.length + 16];
        System.arraycopy(prefix, 0, content, 0, prefix.length);
        System.arraycopy(body, 0, content, prefix.length, body.length);
        return content;
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static UUID uuid(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static String text(Map<String, Object> row, String key) {
        return row.get(key).toString();
    }

    private static Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instantValue) {
            return instantValue;
        }
        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalArgumentException("unsupported time type: " + value.getClass().getName());
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("serviceos-evidence-correction-it");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private DecideReviewCaseCommand decideCommand(
            UUID reviewCaseId, String overall, List<String> reasonCodes, String note
    ) {
        ReviewCaseView reviewCase = reviews.get(reviewer(), "corr-decide-load", reviewCaseId);
        EvidenceSetSnapshotView snapshot = snapshots.get(
                reviewer(), "corr-decide-targets", reviewCase.evidenceSetSnapshotId());
        List<ReviewTargetDecisionCommand> targets = snapshot.members().stream()
                .map(member -> new ReviewTargetDecisionCommand(
                        "EvidenceRevision",
                        member.evidenceRevisionId(),
                        member.revisionNumber(),
                        overall,
                        "REJECTED".equals(overall) ? reasonCodes : List.of(),
                        "REJECTED".equals(overall)
                                ? (note == null || note.isBlank() ? "需要整改" : note)
                                : null))
                .toList();
        return new DecideReviewCaseCommand(
                reviewCase.reviewCaseId(), targets, note, reviewCase.aggregateVersion());
    }

}
