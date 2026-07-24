package com.serviceos.evidence.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.evidence.api.BeginEvidenceUploadCommand;
import com.serviceos.evidence.api.CreateEvidenceSetSnapshotCommand;
import com.serviceos.evidence.api.CreateClientReviewCaseCommand;
import com.serviceos.evidence.api.CreateReviewCaseCommand;
import com.serviceos.evidence.api.DecideReviewCaseCommand;
import com.serviceos.evidence.api.ReviewTargetDecisionCommand;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.EvidenceCommandService;
import com.serviceos.evidence.api.ExternalReviewAffectedTarget;
import com.serviceos.evidence.api.EvidenceSetSnapshotService;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.FinalizeEvidenceUploadCommand;
import com.serviceos.evidence.api.ExternalReviewReceiptService;
import com.serviceos.evidence.api.ExternalReviewReceiptView;
import com.serviceos.evidence.api.ForceApproveReviewCaseCommand;
import com.serviceos.evidence.api.RecordExternalReviewReceiptCommand;
import com.serviceos.evidence.api.ReopenReviewCaseCommand;
import com.serviceos.evidence.api.ReviewCaseService;
import com.serviceos.evidence.api.ReviewCaseQueryService;
import com.serviceos.evidence.api.ReviewCaseQueueQuery;
import com.serviceos.evidence.api.ReviewCaseView;
import com.serviceos.files.infrastructure.LocalObjectTransferService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.integration.api.ExternalReviewRouteService;
import com.serviceos.integration.api.OutboundDeliveryService;
import com.serviceos.integration.api.OutboundDeliveryView;
import com.serviceos.integration.api.CreateReviewSubmissionCommand;
import com.serviceos.integration.api.RegisterExternalReviewRouteCommand;
import com.serviceos.integration.api.RetryOutboundDeliveryCommand;
import com.serviceos.integration.byd.api.BydCpimReviewCallbackResponse;
import com.serviceos.integration.byd.api.BydCpimSignatureHeaders;
import com.serviceos.integration.byd.application.BydCpimReviewCallbackService;
import com.serviceos.integration.byd.infrastructure.BydCpimSignatureVerifier;
import com.serviceos.integration.byd.spi.BydCpimSubmitReviewGateway;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.task.application.TaskExecutionWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Clock;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReviewCasePostgresIT {
    private static final String TENANT = "tenant-review-case-it";
    private static final String TECHNICIAN = "technician-evidence-044";
    private static final String REVIEWER = "reviewer-evidence-044";
    private static final String FORCE_ADMIN = "force-admin-evidence-048";
    private static final String ADAPTER = "byd-adapter-evidence-049";
    private static final String OPS_USER = "integration-ops-user-059";
    private static final String CPIM_APP_KEY = "review-callback-app-key";
    private static final String CPIM_APP_SECRET = "review-callback-app-secret";
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
                () -> "evidence-review-it-signing-key-with-thirty-two-bytes");
        registry.add("serviceos.task.scheduling-enabled", () -> "false");
        registry.add("serviceos.integration.byd.cpim.app-key", () -> CPIM_APP_KEY);
        registry.add("serviceos.integration.byd.cpim.app-secret", () -> CPIM_APP_SECRET);
        registry.add("serviceos.integration.byd.cpim.zone-id", () -> "Asia/Shanghai");
        registry.add("serviceos.integration.byd.cpim.tenant-id", () -> TENANT);
        registry.add("serviceos.integration.byd.cpim.adapter-principal-id", () -> ADAPTER);
        registry.add("serviceos.integration.byd.cpim.credential-version-id", () -> "cred-review-it-v1");
    }

    @Autowired ConfigurationService configurations;
    @Autowired EvidenceCommandService evidence;
    @Autowired EvidenceSetSnapshotService snapshots;
    @Autowired ReviewCaseService reviews;
    @Autowired ReviewCaseQueryService reviewQueries;
    @Autowired ExternalReviewReceiptService receipts;
    @Autowired ExternalReviewRouteService reviewRoutes;
    @Autowired BydCpimReviewCallbackService reviewCallbacks;
    @Autowired OutboundDeliveryService outboundDeliveries;
    @MockitoBean BydCpimSubmitReviewGateway submitReviewGateway;
    @Autowired Clock clock;
    @Autowired LocalObjectTransferService transfers;
    @Autowired TaskExecutionWorker worker;
    @Autowired List<OutboxMessageHandler> handlers;
    @Autowired JdbcClient jdbc;

    UUID projectId;
    UUID workOrderId;
    UUID slotId;
    UUID taskId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("""
                TRUNCATE TABLE
                    rdm_work_order_timeline_entry,
                    int_delivery_replay_request, int_external_acknowledgement,
                    int_delivery_attempt, int_outbound_delivery,
                    int_inbound_item_result, int_external_review_route,
                    int_canonical_message, int_inbound_envelope, int_inbound_replay_guard,
                    evd_external_review_receipt, evd_external_receipt_command_result,
                    evd_correction_resubmission, evd_correction_case, evd_correction_command_result,
                    evd_review_decision, evd_review_case, evd_review_command_result,
                    evd_evidence_set_member, evd_evidence_set_snapshot,
                    evd_evidence_validation, evd_evidence_command_result, evd_evidence_revision,
                    evd_evidence_item, evd_evidence_upload_session, evd_evidence_slot,
                    evd_task_evidence_resolution,
                    fil_download_authorization, fil_scan_result, fil_stored_file, fil_upload_session,
                    ops_task_failure_recovery, ops_exception_ack_result, ops_operational_exception,
                    tsk_task_execution_guard, tsk_task_assignment, tsk_task, wo_work_order,
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
                VALUES (:projectId, :tenantId, 'EVD-REV-IT', 'BYD', '资料审核测试项目',
                    :startsOn, 'ACTIVE', 1, now())
                """).param("projectId", projectId).param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1)).update();
        grant(TECHNICIAN, "evidence.submit", "evidence.read", "file.upload", "file.download");
        grant(REVIEWER, "evidence.review", "evidence.read");
        grant(FORCE_ADMIN, "evidence.forceApprove", "review.reopen", "evidence.read", "evidence.review");
        grant(ADAPTER, "evidence.createClientReviewCase", "evidence.recordExternalReceipt",
                "evidence.read", "integration.registerExternalReviewRoute",
                "integration.submitClientReview", "integration.readOutbound");
        grant(OPS_USER, "integration.retryUnknownDelivery", "integration.readOutbound");
        seedResolvedSlot();
    }

    @Test
    void bydReviewSubmissionFailsClosedWhenFrozenBundleLacksOutboundMapping() throws Exception {
        ReviewCaseView source = createApprovedInternalWithBydLineage("outbound-no-mapping", "ORDER-OUT-0");
        // M331：切换到仅含 EVIDENCE 的冻结 Bundle，不得回退 Profile 硬编码 payload。
        String evidenceOnly = """
                {"templateKey":"survey.site.nomap","version":"1.0.0","stage":"SURVEY",
                 "items":[{"evidenceKey":"site.photo","name":"现场照片","mediaType":"PHOTO","required":true,
                   "capture":{"minCount":1,"maxCount":2}}]}
                """;
        UUID evidenceOnlyAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.EVIDENCE, "survey.site.nomap", "1.0.0", "1.0.0",
                evidenceOnly.trim(), Sha256.digest(evidenceOnly.trim()))).versionId();
        ConfigurationBundleReference noOutboundBundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        TENANT, projectId, "EVD-REV-NO-OUTBOUND", "1.0.0", "BYD_NO_OUTBOUND", "HOME",
                        null, Instant.now().minusSeconds(30), null, List.of(evidenceOnlyAsset)));
        jdbc.sql("""
                UPDATE tsk_task
                   SET configuration_bundle_id = :bundle,
                       configuration_bundle_digest = :digest
                 WHERE task_id = :task
                """)
                .param("bundle", noOutboundBundle.bundleId())
                .param("digest", noOutboundBundle.manifestDigest())
                .param("task", taskId)
                .update();

        assertThatThrownBy(() -> outboundDeliveries.createReviewSubmission(
                adapter(), metadata("outbound-no-mapping"),
                new CreateReviewSubmissionCommand(source.reviewCaseId())))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
        assertThat(jdbc.sql("SELECT count(*) FROM int_outbound_delivery").query(Long.class).single())
                .isZero();
    }

    @Test
    void bydReviewSubmissionCreatesImmutableDeliveryAndClientReviewRouteAfterErrnoZero() throws Exception {
        ReviewCaseView source = createApprovedInternalWithBydLineage("outbound-success", "ORDER-OUT-1");
        when(submitReviewGateway.send(any())).thenReturn(new BydCpimSubmitReviewGateway.Response(
                200, "{\"errno\":0,\"errmsg\":\"成功\",\"data\":null}".getBytes(StandardCharsets.UTF_8)));

        OutboundDeliveryView created = outboundDeliveries.createReviewSubmission(
                adapter(), metadata("outbound-success"),
                new CreateReviewSubmissionCommand(source.reviewCaseId()));

        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(created.executionTaskId()).isNotNull();
        // mappingVersionId 必须是 Mapping 资产 UUID，不再是 Profile 字符串常量。
        assertThat(created.mappingVersionId()).matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name = 'OUTBOUND_INTEGRATION_MAPPING_APPLIED'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);

        OutboundDeliveryView completed = outboundDeliveries.get(
                adapter(), "corr-outbound-query", created.deliveryId());
        assertThat(completed.status()).isEqualTo("ACKNOWLEDGED");
        assertThat(completed.clientReviewCaseId()).isNotNull();
        assertThat(completed.reviewRouteId()).isNotNull();
        assertThat(completed.attempts()).singleElement().satisfies(attempt -> {
            assertThat(attempt.status()).isEqualTo("DELIVERED");
            assertThat(attempt.resultCode()).isEqualTo("BYD_ERRNO_0");
        });
        assertThat(completed.acknowledgements()).singleElement().satisfies(ack -> {
            assertThat(ack.result()).isEqualTo("ACCEPTED");
            assertThat(ack.reasonCode()).isEqualTo("BYD_ACCEPTED");
        });
        assertThat(reviews.get(adapter(), "corr-client-after-delivery", completed.clientReviewCaseId()).origin())
                .isEqualTo("CLIENT");
        assertThat(jdbc.sql("SELECT status FROM int_external_review_route WHERE review_route_id=:id")
                .param("id", completed.reviewRouteId()).query(String.class).single()).isEqualTo("ACTIVE");

        OutboundDeliveryView replay = outboundDeliveries.createReviewSubmission(
                adapter(), metadata("outbound-business-replay"),
                new CreateReviewSubmissionCommand(source.reviewCaseId()));
        assertThat(replay.deliveryId()).isEqualTo(created.deliveryId());
        assertThat(jdbc.sql("SELECT count(*) FROM int_outbound_delivery").query(Long.class).single()).isOne();
        verify(submitReviewGateway, times(1)).send(any());
    }

    @Test
    void bydReviewSubmissionRetriesOnlyKnownDeliveredLocalFinalizationWithoutSecondHttpCall() throws Exception {
        ReviewCaseView source = createApprovedInternalWithBydLineage("outbound-local-retry", "ORDER-OUT-2");
        UUID adapterRole = jdbc.sql("""
                SELECT arc.role_id FROM auth_role_capability arc
                JOIN auth_role_grant arg ON arg.role_id=arc.role_id
                WHERE arg.principal_id=:principal AND arc.capability_code='evidence.createClientReviewCase'
                LIMIT 1
                """).param("principal", ADAPTER).query(UUID.class).single();
        jdbc.sql("DELETE FROM auth_role_capability WHERE role_id=:role AND capability_code='evidence.createClientReviewCase'")
                .param("role", adapterRole).update();
        when(submitReviewGateway.send(any())).thenReturn(new BydCpimSubmitReviewGateway.Response(
                200, "{\"errno\":0,\"errmsg\":\"成功\",\"data\":null}".getBytes(StandardCharsets.UTF_8)));
        OutboundDeliveryView created = outboundDeliveries.createReviewSubmission(
                adapter(), metadata("outbound-local-retry"),
                new CreateReviewSubmissionCommand(source.reviewCaseId()));

        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.RETRY_SCHEDULED);
        assertThat(outboundDeliveries.get(adapter(), "corr-delivered", created.deliveryId()).status())
                .isEqualTo("DELIVERED");

        jdbc.sql("INSERT INTO auth_role_capability(role_id, capability_code, granted_at) VALUES (:role,'evidence.createClientReviewCase',now())")
                .param("role", adapterRole).update();
        jdbc.sql("UPDATE tsk_task SET next_run_at=:nextRunAt WHERE task_id=:id")
                .param("nextRunAt", java.time.OffsetDateTime.ofInstant(
                        clock.instant().minusSeconds(1), ZoneId.of("UTC")))
                .param("id", created.executionTaskId()).update();
        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        assertThat(outboundDeliveries.get(adapter(), "corr-ack-after-retry", created.deliveryId()).status())
                .isEqualTo("ACKNOWLEDGED");
        verify(submitReviewGateway, times(1)).send(any());
    }

    @Test
    void bydReviewSubmissionUnknownNeverResendsAndOpensOperationalException() throws Exception {
        ReviewCaseView source = createApprovedInternalWithBydLineage("outbound-unknown", "ORDER-OUT-3");
        when(submitReviewGateway.send(any())).thenThrow(new BydCpimSubmitReviewGateway.TransportException(
                BydCpimSubmitReviewGateway.TransportException.Kind.UNKNOWN,
                "BYD_TRANSPORT_UNKNOWN", new IOException("response lost")));
        OutboundDeliveryView created = outboundDeliveries.createReviewSubmission(
                adapter(), metadata("outbound-unknown"),
                new CreateReviewSubmissionCommand(source.reviewCaseId()));

        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.MANUAL_INTERVENTION);
        assertThat(outboundDeliveries.get(adapter(), "corr-unknown", created.deliveryId()).status())
                .isEqualTo("UNKNOWN");
        OutboxMessage failure = latestEvent("task.execution.manual-intervention-required");
        handlers.stream().filter(handler -> handler.supports(failure.eventType(), failure.schemaVersion()))
                .forEach(handler -> handler.handle(failure));

        assertThat(jdbc.sql("SELECT count(*) FROM ops_operational_exception WHERE source_id=:task")
                .param("task", created.executionTaskId().toString()).query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM tsk_task WHERE task_type='operations.resolve-exception'")
                .query(Long.class).single()).isOne();
        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.EMPTY);
        verify(submitReviewGateway, times(1)).send(any());
    }

    @Test
    void authorizedManualReplayPreservesUnknownAttemptAndFrozenPayload() throws Exception {
        ReviewCaseView source = createApprovedInternalWithBydLineage("outbound-replay", "ORDER-OUT-4");
        when(submitReviewGateway.send(any()))
                .thenThrow(new BydCpimSubmitReviewGateway.TransportException(
                        BydCpimSubmitReviewGateway.TransportException.Kind.UNKNOWN,
                        "BYD_TRANSPORT_UNKNOWN", new IOException("response lost")))
                .thenReturn(new BydCpimSubmitReviewGateway.Response(
                        200, "{\"errno\":0,\"errmsg\":\"成功\",\"data\":null}"
                                .getBytes(StandardCharsets.UTF_8)));
        OutboundDeliveryView created = outboundDeliveries.createReviewSubmission(
                adapter(), metadata("outbound-replay-create"),
                new CreateReviewSubmissionCommand(source.reviewCaseId()));
        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.MANUAL_INTERVENTION);
        OutboundDeliveryView unknown = outboundDeliveries.get(
                opsUser(), "corr-replay-unknown", created.deliveryId());
        assertThat(unknown.status()).isEqualTo("UNKNOWN");
        OutboxMessage originalFailure = latestEvent("task.execution.manual-intervention-required");
        handlers.stream()
                .filter(handler -> handler.supports(
                        originalFailure.eventType(), originalFailure.schemaVersion()))
                .forEach(handler -> handler.handle(originalFailure));
        assertThatThrownBy(() -> jdbc.sql("""
                UPDATE int_outbound_delivery SET status='SENDING', aggregate_version=aggregate_version+1
                 WHERE delivery_id=:id
                """).param("id", created.deliveryId()).update())
                .isInstanceOf(org.springframework.dao.DataAccessException.class);

        var replay = outboundDeliveries.retryUnknown(
                opsUser(), metadata("outbound-replay-request"),
                new RetryOutboundDeliveryCommand(
                        created.deliveryId(), unknown.aggregateVersion(),
                        "已核对车企侧未收到原请求，批准复用冻结报文重发",
                        "approval://integration/059/1"));
        var idempotentReplay = outboundDeliveries.retryUnknown(
                opsUser(), metadata("outbound-replay-request"),
                new RetryOutboundDeliveryCommand(
                        created.deliveryId(), unknown.aggregateVersion(),
                        "已核对车企侧未收到原请求，批准复用冻结报文重发",
                        "approval://integration/059/1"));
        assertThat(idempotentReplay.replayRequestId()).isEqualTo(replay.replayRequestId());
        assertThat(replay.status()).isEqualTo("REQUESTED");
        assertThatThrownBy(() -> outboundDeliveries.retryUnknown(
                opsUser(), metadata("outbound-replay-concurrent"),
                new RetryOutboundDeliveryCommand(
                        created.deliveryId(), unknown.aggregateVersion(),
                        "另一个并发重发请求", "approval://integration/059/2")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VERSION_CONFLICT));
        UUID adapterRole = jdbc.sql("""
                SELECT arc.role_id FROM auth_role_capability arc
                JOIN auth_role_grant arg ON arg.role_id=arc.role_id
                WHERE arg.principal_id=:principal AND arc.capability_code='evidence.createClientReviewCase'
                LIMIT 1
                """).param("principal", ADAPTER).query(UUID.class).single();
        jdbc.sql("DELETE FROM auth_role_capability WHERE role_id=:role AND capability_code='evidence.createClientReviewCase'")
                .param("role", adapterRole).update();
        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.RETRY_SCHEDULED);
        assertThat(outboundDeliveries.get(
                opsUser(), "corr-replay-delivered", created.deliveryId()).status()).isEqualTo("DELIVERED");
        verify(submitReviewGateway, times(2)).send(any());

        jdbc.sql("INSERT INTO auth_role_capability(role_id, capability_code, granted_at) VALUES (:role,'evidence.createClientReviewCase',now())")
                .param("role", adapterRole).update();
        jdbc.sql("UPDATE tsk_task SET next_run_at=:nextRunAt WHERE task_id=:id")
                .param("nextRunAt", java.time.OffsetDateTime.ofInstant(
                        clock.instant().minusSeconds(1), ZoneId.of("UTC")))
                .param("id", replay.executionTaskId()).update();
        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);

        OutboundDeliveryView completed = outboundDeliveries.get(
                opsUser(), "corr-replay-completed", created.deliveryId());
        assertThat(completed.status()).isEqualTo("ACKNOWLEDGED");
        assertThat(completed.payloadDigest()).isEqualTo(created.payloadDigest());
        assertThat(completed.externalIdempotencyKey()).isEqualTo(created.externalIdempotencyKey());
        assertThat(completed.attempts()).hasSize(2);
        assertThat(completed.attempts().get(0).status()).isEqualTo("UNKNOWN");
        assertThat(completed.attempts().get(1).status()).isEqualTo("DELIVERED");
        assertThat(completed.replayRequests()).singleElement().satisfies(result -> {
            assertThat(result.replayRequestId()).isEqualTo(replay.replayRequestId());
            assertThat(result.status()).isEqualTo("DELIVERED");
            assertThat(result.resultCode()).isEqualTo("BYD_ERRNO_0");
            assertThat(result.approvalRef()).isEqualTo("approval://integration/059/1");
        });
        OutboxMessage recovery = latestEvent("integration.outbound-delivery-recovered");
        handlers.stream()
                .filter(handler -> handler.supports(recovery.eventType(), recovery.schemaVersion()))
                .forEach(handler -> handler.handle(recovery));
        assertThat(jdbc.sql("""
                SELECT status || ':' || resolution_code
                  FROM ops_operational_exception WHERE source_id=:sourceTaskId
                """).param("sourceTaskId", created.executionTaskId().toString())
                .query(String.class).single())
                .isEqualTo("RESOLVED:OUTBOUND_DELIVERY_RECOVERED");
        assertThat(jdbc.sql("""
                SELECT status FROM tsk_task
                 WHERE task_type='operations.resolve-exception'
                """).query(String.class).single()).isEqualTo("CANCELLED");
        assertThat(jdbc.sql("SELECT count(*) FROM ops_task_failure_recovery")
                .query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_outbox_event
                 WHERE event_type='operational.exception.resolved' AND schema_version=2
                """).query(Long.class).single()).isOne();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_outbox_event
                 WHERE event_type='integration.outbound-delivery-replay-requested'
                """).query(Long.class).single()).isOne();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name='OUTBOUND_DELIVERY_REPLAY_REQUESTED' AND actor_id=:actor
                """).param("actor", OPS_USER).query(Long.class).single()).isOne();
        verify(submitReviewGateway, times(2)).send(any());
    }

    @Test
    void manualReplayThatIsStillUnknownReturnsToManualInterventionWithoutAutomaticThirdSend() throws Exception {
        ReviewCaseView source = createApprovedInternalWithBydLineage(
                "outbound-replay-unknown", "ORDER-OUT-6");
        when(submitReviewGateway.send(any())).thenThrow(new BydCpimSubmitReviewGateway.TransportException(
                BydCpimSubmitReviewGateway.TransportException.Kind.UNKNOWN,
                "BYD_TRANSPORT_UNKNOWN", new IOException("response lost")));
        OutboundDeliveryView created = outboundDeliveries.createReviewSubmission(
                adapter(), metadata("outbound-replay-unknown-create"),
                new CreateReviewSubmissionCommand(source.reviewCaseId()));
        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.MANUAL_INTERVENTION);
        OutboundDeliveryView firstUnknown = outboundDeliveries.get(
                opsUser(), "corr-first-unknown", created.deliveryId());
        outboundDeliveries.retryUnknown(
                opsUser(), metadata("outbound-replay-still-unknown"),
                new RetryOutboundDeliveryCommand(
                        created.deliveryId(), firstUnknown.aggregateVersion(),
                        "已审批重发但网络结果仍可能已送达", "approval://integration/059/unknown"));

        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.MANUAL_INTERVENTION);
        OutboundDeliveryView secondUnknown = outboundDeliveries.get(
                opsUser(), "corr-second-unknown", created.deliveryId());
        assertThat(secondUnknown.status()).isEqualTo("UNKNOWN");
        assertThat(secondUnknown.attempts()).hasSize(2).allMatch(attempt -> "UNKNOWN".equals(attempt.status()));
        assertThat(secondUnknown.replayRequests()).singleElement()
                .satisfies(replay -> assertThat(replay.status()).isEqualTo("UNKNOWN"));
        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.EMPTY);
        verify(submitReviewGateway, times(2)).send(any());
    }

    @Test
    void manualReplayFailsClosedForServicePrincipalMissingApprovalAndStaleVersion() throws Exception {
        ReviewCaseView source = createApprovedInternalWithBydLineage("outbound-replay-deny", "ORDER-OUT-5");
        when(submitReviewGateway.send(any())).thenThrow(new BydCpimSubmitReviewGateway.TransportException(
                BydCpimSubmitReviewGateway.TransportException.Kind.UNKNOWN,
                "BYD_TRANSPORT_UNKNOWN", new IOException("response lost")));
        OutboundDeliveryView created = outboundDeliveries.createReviewSubmission(
                adapter(), metadata("outbound-replay-deny-create"),
                new CreateReviewSubmissionCommand(source.reviewCaseId()));
        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.MANUAL_INTERVENTION);
        OutboundDeliveryView unknown = outboundDeliveries.get(
                opsUser(), "corr-replay-deny-unknown", created.deliveryId());

        assertThatThrownBy(() -> outboundDeliveries.retryUnknown(
                adapter(), metadata("outbound-replay-service-denied"),
                new RetryOutboundDeliveryCommand(created.deliveryId(), unknown.aggregateVersion(),
                        "人工重发", "approval://integration/059/deny")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
        assertThatThrownBy(() -> outboundDeliveries.retryUnknown(
                opsUser(), metadata("outbound-replay-approval-missing"),
                new RetryOutboundDeliveryCommand(created.deliveryId(), unknown.aggregateVersion(),
                        "人工重发", " ")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> outboundDeliveries.retryUnknown(
                opsUser(), metadata("outbound-replay-stale"),
                new RetryOutboundDeliveryCommand(created.deliveryId(), unknown.aggregateVersion() - 1,
                        "人工重发", "approval://integration/059/stale")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VERSION_CONFLICT));
        assertThat(jdbc.sql("SELECT count(*) FROM int_delivery_replay_request")
                .query(Long.class).single()).isZero();
        verify(submitReviewGateway, times(1)).send(any());
    }

    @Test
    void bydReviewCallbackProcessesRegisteredOrderAndPersistsPartialFailure() throws Exception {
        ReviewCaseView routedCase = createClientCase(
                createSnapshot("callback-routed"), "callback-routed", "BATCH-CB-1",
                        "byd-ocean-shandong-review-callback-v1");
        reviewRoutes.register(adapter(), metadata("route-callback-routed"),
                new RegisterExternalReviewRouteCommand(
                        "ORDER-CB-1", routedCase.reviewCaseId(), routedCase.externalSubmissionRef(),
                        routedCase.callbackBatchRef(), routedCase.mappingVersionId()));

        Map<String, Object> payload = Map.of(
                "orderCode", "ORDER-CB-1,ORDER-NO-ROUTE",
                "result", "1",
                "remark", "厂端审核通过",
                "examinePerson", "比亚迪审核员",
                "examineDate", "2026-07-15 09:30:00");
        LocalDate protocolDate = LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
        String nonce = "review-callback-nonce-1";
        String signature = new BydCpimSignatureVerifier(
                CPIM_APP_KEY, CPIM_APP_SECRET, clock, ZoneId.of("Asia/Shanghai"))
                .sign(nonce, protocolDate, payload);

        BydCpimReviewCallbackResponse response = reviewCallbacks.receive(
                new BydCpimSignatureHeaders(CPIM_APP_KEY, nonce, protocolDate, signature),
                new tools.jackson.databind.ObjectMapper().writeValueAsBytes(payload), "corr-review-callback");

        assertThat(response.message()).isEqualTo("partially success");
        assertThat(response.data()).singleElement().satisfies(failure -> {
            assertThat(failure.orderCode()).isEqualTo("ORDER-NO-ROUTE");
            assertThat(failure.reason()).isEqualTo("ROUTE_NOT_FOUND");
        });
        assertThat(reviews.get(adapter(), "callback-result", routedCase.reviewCaseId()).status())
                .isEqualTo("APPROVED");
        assertThat(jdbc.sql("SELECT count(*) FROM evd_external_review_receipt")
                .query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM evd_review_decision WHERE decision_source='EXTERNAL'")
                .query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM int_inbound_item_result")
                .query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM tsk_task WHERE task_type='integration.external-review-manual'")
                .query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type="
                        + "'integration.external-review-callback-processed'")
                .query(Long.class).single()).isOne();

        BydCpimReviewCallbackResponse replay = reviewCallbacks.receive(
                new BydCpimSignatureHeaders(CPIM_APP_KEY, nonce, protocolDate, signature),
                new tools.jackson.databind.ObjectMapper().writeValueAsBytes(payload), "corr-review-callback-replay");
        assertThat(replay).isEqualTo(response);
        assertThat(jdbc.sql("SELECT count(*) FROM evd_external_review_receipt")
                .query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM int_inbound_envelope")
                .query(Long.class).single()).isOne();

        String secondNonce = "review-callback-nonce-2";
        String secondSignature = new BydCpimSignatureVerifier(
                CPIM_APP_KEY, CPIM_APP_SECRET, clock, ZoneId.of("Asia/Shanghai"))
                .sign(secondNonce, protocolDate, payload);
        BydCpimReviewCallbackResponse businessReplay = reviewCallbacks.receive(
                new BydCpimSignatureHeaders(CPIM_APP_KEY, secondNonce, protocolDate, secondSignature),
                new tools.jackson.databind.ObjectMapper().writeValueAsBytes(payload), "corr-review-business-replay");
        assertThat(businessReplay).isEqualTo(response);
        assertThat(jdbc.sql("SELECT count(*) FROM int_inbound_envelope")
                .query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM int_canonical_message")
                .query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM evd_external_review_receipt")
                .query(Long.class).single()).isOne();

        Map<String, Object> conflictingPayload = Map.of(
                "orderCode", "ORDER-CB-1,ORDER-NO-ROUTE",
                "result", "1",
                "remark", "同一审核事实被改写",
                "examinePerson", "比亚迪审核员",
                "examineDate", "2026-07-15 09:30:00");
        String conflictNonce = "review-callback-nonce-3";
        String conflictSignature = new BydCpimSignatureVerifier(
                CPIM_APP_KEY, CPIM_APP_SECRET, clock, ZoneId.of("Asia/Shanghai"))
                .sign(conflictNonce, protocolDate, conflictingPayload);
        BydCpimReviewCallbackResponse conflict = reviewCallbacks.receive(
                new BydCpimSignatureHeaders(CPIM_APP_KEY, conflictNonce, protocolDate, conflictSignature),
                new tools.jackson.databind.ObjectMapper().writeValueAsBytes(conflictingPayload),
                "corr-review-business-conflict");

        assertThat(conflict.message()).isEqualTo("partially success");
        assertThat(conflict.data()).hasSize(2).allSatisfy(failure ->
                assertThat(failure.reason()).isEqualTo("CANONICAL_CONFLICT"));
        assertThat(jdbc.sql("SELECT count(*) FROM int_inbound_envelope")
                .query(Long.class).single()).isEqualTo(3);
        assertThat(jdbc.sql("SELECT count(*) FROM int_canonical_message")
                .query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM evd_external_review_receipt")
                .query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM tsk_task WHERE task_type='integration.external-review-manual'")
                .query(Long.class).single()).isEqualTo(3);
    }

    @Test
    void bydReviewCallbackRejectsClientCaseAndKeepsPrivateRemarkOutOfDomainFacts() throws Exception {
        ReviewCaseView client = createClientCase(
                createSnapshot("callback-reject"), "callback-reject", "BATCH-CB-REJECT",
                        "byd-ocean-shandong-review-callback-v1");
        reviewRoutes.register(adapter(), metadata("route-callback-reject"),
                new RegisterExternalReviewRouteCommand(
                        "ORDER-CB-REJECT", client.reviewCaseId(), client.externalSubmissionRef(),
                        client.callbackBatchRef(), client.mappingVersionId()));

        BydCpimReviewCallbackResponse response = receiveReviewCallback(
                "review-callback-reject", Map.of(
                        "orderCode", "ORDER-CB-REJECT", "result", "2", "remark", "厂端资料不完整",
                        "examinePerson", "比亚迪审核员", "examineDate", "2026-07-15 10:00:00"));

        assertThat(response.message()).isEqualTo("success");
        assertThat(reviews.get(adapter(), "callback-rejected-case", client.reviewCaseId()).status())
                .isEqualTo("REJECTED");
        assertThat(jdbc.sql("SELECT reason_codes::text FROM evd_review_decision WHERE decision_source='EXTERNAL'")
                .query(String.class).single()).contains("BYD.REVIEW.REJECTED").doesNotContain("厂端资料不完整");
        assertThat(jdbc.sql("SELECT count(*) FROM tsk_task WHERE task_type='evidence.external-coordination'")
                .query(Long.class).single()).isOne();
    }

    @Test
    void bydReviewCallbackLeavesEnvelopeReceivedUntilAuthorizationFailureIsRepaired() throws Exception {
        ReviewCaseView client = createClientCase(
                createSnapshot("callback-recovery"), "callback-recovery", "BATCH-CB-REC",
                        "byd-ocean-shandong-review-callback-v1");
        reviewRoutes.register(adapter(), metadata("route-callback-recovery"),
                new RegisterExternalReviewRouteCommand(
                        "ORDER-CB-REC", client.reviewCaseId(), client.externalSubmissionRef(),
                        client.callbackBatchRef(), client.mappingVersionId()));
        UUID adapterRole = jdbc.sql("""
                SELECT arc.role_id FROM auth_role_capability arc
                JOIN auth_role_grant arg ON arg.role_id=arc.role_id
                WHERE arg.principal_id=:principal AND arc.capability_code='evidence.recordExternalReceipt'
                LIMIT 1
                """).param("principal", ADAPTER).query(UUID.class).single();
        jdbc.sql("DELETE FROM auth_role_capability WHERE role_id=:role AND capability_code='evidence.recordExternalReceipt'")
                .param("role", adapterRole).update();
        Map<String, Object> payload = Map.of(
                "orderCode", "ORDER-CB-REC", "result", "1", "remark", "通过",
                "examinePerson", "比亚迪审核员", "examineDate", "2026-07-15 10:30:00");

        assertThatThrownBy(() -> receiveReviewCallback("review-callback-recovery", payload))
                .isInstanceOf(BusinessProblem.class);
        assertThat(jdbc.sql("SELECT processing_status FROM int_inbound_envelope")
                .query(String.class).single()).isEqualTo("RECEIVED");
        assertThat(jdbc.sql("SELECT count(*) FROM int_canonical_message")
                .query(Long.class).single()).isZero();

        jdbc.sql("INSERT INTO auth_role_capability(role_id, capability_code, granted_at) VALUES (:role,'evidence.recordExternalReceipt',now())")
                .param("role", adapterRole).update();
        BydCpimReviewCallbackResponse recovered = receiveReviewCallback("review-callback-recovery", payload);
        assertThat(recovered.message()).isEqualTo("success");
        assertThat(jdbc.sql("SELECT processing_status FROM int_inbound_envelope")
                .query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT count(*) FROM evd_external_review_receipt")
                .query(Long.class).single()).isOne();
    }

    @Test
    void workflowReviewSubmissionRequiredCreatesOneInternalReviewAndHandlingTask() throws Exception {
        EvidenceSetSnapshotView snapshot = createSnapshot("workflow-review-required");
        UUID workflowInstanceId = UUID.randomUUID();
        UUID reviewNodeInstanceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant requiredAt = clock.instant();
        String payload = """
                {
                  "workflowInstanceId":"%s",
                  "reviewNodeInstanceId":"%s",
                  "projectId":"%s",
                  "workOrderId":"%s",
                  "sourceTaskId":"%s",
                  "evidenceSetSnapshotId":"%s",
                  "snapshotContentDigest":"%s",
                  "requiredAt":"%s"
                }
                """.formatted(
                workflowInstanceId, reviewNodeInstanceId, projectId, workOrderId,
                taskId, snapshot.evidenceSetSnapshotId(), snapshot.contentDigest(), requiredAt);
        OutboxMessage message = new OutboxMessage(
                UUID.randomUUID(), eventId, "workflow",
                "workflow.review-submission-required", 1,
                "Workflow", workflowInstanceId.toString(), 1,
                TENANT, "corr-workflow-review-required", UUID.randomUUID().toString(),
                workOrderId.toString(), payload, Sha256.digest(payload), requiredAt, 1);
        OutboxMessageHandler handler = handlers.stream()
                .filter(candidate -> candidate.supports(message.eventType(), message.schemaVersion()))
                .findFirst()
                .orElseThrow();

        handler.handle(message);
        handler.handle(message);

        Map<String, Object> created = jdbc.sql("""
                SELECT review_case_id, review_task_id, status, origin
                  FROM evd_review_case
                 WHERE tenant_id=:tenant
                   AND evidence_set_snapshot_id=:snapshotId
                """)
                .param("tenant", TENANT)
                .param("snapshotId", snapshot.evidenceSetSnapshotId())
                .query().singleRow();
        assertThat(text(created, "status")).isEqualTo("OPEN");
        assertThat(text(created, "origin")).isEqualTo("INTERNAL");
        assertThat(jdbc.sql("""
                SELECT status FROM tsk_task WHERE tenant_id=:tenant AND task_id=:taskId
                """)
                .param("tenant", TENANT)
                .param("taskId", uuid(created, "review_task_id"))
                .query(String.class).single()).isEqualTo("READY");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM evd_review_case
                 WHERE tenant_id=:tenant AND evidence_set_snapshot_id=:snapshotId
                """)
                .param("tenant", TENANT)
                .param("snapshotId", snapshot.evidenceSetSnapshotId())
                .query(Long.class).single()).isOne();
    }

    @Test
    void createsApprovesAndReplaysWithoutDuplicateDecisions() throws Exception {
        EvidenceSetSnapshotView snapshot = createSnapshot("approve");

        ReviewCaseView created = reviews.create(reviewer(), metadata("create-1"),
                new CreateReviewCaseCommand(snapshot.evidenceSetSnapshotId(), null));
        ReviewCaseView replay = reviews.create(reviewer(), metadata("create-1"),
                new CreateReviewCaseCommand(snapshot.evidenceSetSnapshotId(), null));

        assertThat(replay.reviewCaseId()).isEqualTo(created.reviewCaseId());
        assertThat(created.status()).isEqualTo("OPEN");
        assertThat(created.decisions()).isEmpty();
        assertThat(created.reviewTaskId()).isNotNull();
        assertThat(created.taskId()).isEqualTo(snapshot.taskId());
        assertThat(created.reviewTaskId()).isNotEqualTo(created.taskId());
        assertThat(jdbc.sql("""
                        SELECT status FROM tsk_task
                         WHERE tenant_id = :tenant AND task_id = :taskId
                        """)
                .param("tenant", TENANT).param("taskId", created.reviewTaskId())
                .query(String.class).single()).isEqualTo("READY");
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type='evidence.review-case-created'")
                .query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM aud_audit_record WHERE action_name='REVIEW_CASE_CREATED'")
                .query(Long.class).single()).isOne();

        assertThatThrownBy(() -> reviews.create(reviewer(), metadata("create-dup"),
                new CreateReviewCaseCommand(snapshot.evidenceSetSnapshotId(), null)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.REVIEW_CASE_CONFLICT));

        String sourceStatusBefore = jdbc.sql("""
                        SELECT status FROM tsk_task
                         WHERE tenant_id = :tenant AND task_id = :taskId
                        """)
                .param("tenant", TENANT).param("taskId", created.taskId())
                .query(String.class).single();

        DecideReviewCaseCommand approveCommand = decideCommand(
                created.reviewCaseId(), "APPROVED", List.of(), "ok");
        var decidedResult = reviews.decide(reviewer(), metadata("decide-approve"), approveCommand);
        ReviewCaseView decided = decidedResult.reviewCase();
        var decideReplayResult = reviews.decide(
                reviewer(), metadata("decide-approve"), approveCommand);
        ReviewCaseView decideReplay = decideReplayResult.reviewCase();

        assertThat(decideReplay.reviewCaseId()).isEqualTo(created.reviewCaseId());
        assertThat(decided.status()).isEqualTo("APPROVED");
        assertThat(decided.decidedAt()).isNotNull();
        assertThat(decided.decisions()).hasSize(1);
        assertThat(decided.decisions().getFirst().decision()).isEqualTo("APPROVED");
        assertThat(jdbc.sql("""
                        SELECT status FROM tsk_task
                         WHERE tenant_id = :tenant AND task_id = :taskId
                        """)
                .param("tenant", TENANT).param("taskId", created.reviewTaskId())
                .query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("""
                        SELECT status FROM tsk_task
                         WHERE tenant_id = :tenant AND task_id = :taskId
                        """)
                .param("tenant", TENANT).param("taskId", created.taskId())
                .query(String.class).single()).isEqualTo(sourceStatusBefore);
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type='evidence.review-decided'")
                .query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM aud_audit_record WHERE action_name='REVIEW_CASE_DECIDED'")
                .query(Long.class).single()).isOne();

        assertThatThrownBy(() -> reviews.decide(reviewer(), metadata("decide-again"),
                decideCommand(created.reviewCaseId(), "REJECTED", List.of("MISSING_PHOTO"), "late")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.REVIEW_CASE_ALREADY_DECIDED));
        assertThat(jdbc.sql("SELECT count(*) FROM evd_review_decision").query(Long.class).single()).isOne();
    }

    @Test
    void authorizedReviewQueueDefaultsOpenFiltersAndUsesScopeBoundCursor() throws Exception {
        ReviewCaseView firstCase = reviews.create(
                reviewer(), metadata("queue-create-1"),
                new CreateReviewCaseCommand(createSnapshot("queue-1").evidenceSetSnapshotId(), null));
        ReviewCaseView secondCase = reviews.create(
                reviewer(), metadata("queue-create-2"),
                new CreateReviewCaseCommand(createSnapshot("queue-2").evidenceSetSnapshotId(), "POLICY_QUEUE"));

        var firstPage = reviewQueries.list(
                reviewer(), "corr-review-queue-1",
                new ReviewCaseQueueQuery(projectId, null, "INTERNAL", null, null, 1));
        assertThat(firstPage.items()).hasSize(1);
        assertThat(firstPage.nextCursor()).isNotBlank();
        var secondPage = reviewQueries.list(
                reviewer(), "corr-review-queue-2",
                new ReviewCaseQueueQuery(
                        projectId, "OPEN", "INTERNAL", null, firstPage.nextCursor(), 1));
        assertThat(secondPage.items()).hasSize(1);
        assertThat(List.of(
                firstPage.items().getFirst().reviewCaseId(),
                secondPage.items().getFirst().reviewCaseId()))
                .containsExactlyInAnyOrder(firstCase.reviewCaseId(), secondCase.reviewCaseId());
        assertThat(secondPage.items().getFirst().latestDecisionId()).isNull();
        assertThat(secondPage.toString()).doesNotContain(
                "snapshotContentDigest", "createdBy", "note", "approvalRef", "decidedBy");
        assertThat(reviewQueries.list(
                reviewer(), "corr-review-queue-scope",
                new ReviewCaseQueueQuery(null, "OPEN", null, null, null, 20)).items())
                .extracting(item -> item.reviewCaseId())
                .containsExactlyInAnyOrder(firstCase.reviewCaseId(), secondCase.reviewCaseId());

        assertThatThrownBy(() -> reviewQueries.list(
                reviewer(), "corr-review-queue-cursor",
                new ReviewCaseQueueQuery(
                        projectId, "APPROVED", "INTERNAL", null, firstPage.nextCursor(), 1)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> reviewQueries.list(
                reviewer(), "corr-review-queue-status",
                new ReviewCaseQueueQuery(projectId, "UNKNOWN", null, null, null, 20)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    @Test
    void rejectRequiresReasonCodesAndEnforcesProjectScope() throws Exception {
        EvidenceSetSnapshotView snapshot = createSnapshot("reject");
        ReviewCaseView created = reviews.create(reviewer(), metadata("create-reject"),
                new CreateReviewCaseCommand(snapshot.evidenceSetSnapshotId(), "POLICY_A"));

        assertThatThrownBy(() -> reviews.decide(reviewer(), metadata("reject-empty"),
                decideCommand(created.reviewCaseId(), "REJECTED", List.of(), "no")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));

        var rejectedResult = reviews.decide(reviewer(), metadata("reject-ok"),
                decideCommand(created.reviewCaseId(), "REJECTED", List.of("MISSING_PHOTO", "BLURRY"), "fix"));
        ReviewCaseView rejected = rejectedResult.reviewCase();
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.decisions().getFirst().reasonCodes())
                .containsExactly("MISSING_PHOTO", "BLURRY");

        UUID otherProject = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, project_status, aggregate_version, created_at)
                VALUES (:projectId, :tenantId, 'EVD-REV-OTHER', 'BYD', '其他项目',
                    :startsOn, 'ACTIVE', 1, now())
                """).param("projectId", otherProject).param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1)).update();
        grantOnProject("reviewer-other", otherProject, false, "evidence.review", "evidence.read");

        assertThatThrownBy(() -> reviews.get(
                new CurrentPrincipal("reviewer-other", TENANT, CurrentPrincipal.PrincipalType.USER,
                        "ops-web", Set.of()),
                "corr-scope",
                created.reviewCaseId()))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
    }

    @Test
    void missingSnapshotIsRejectedWithoutCasePollution() {
        assertThatThrownBy(() -> reviews.create(reviewer(), metadata("missing-snap"),
                new CreateReviewCaseCommand(UUID.randomUUID(), null)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
        assertThat(jdbc.sql("SELECT count(*) FROM evd_review_case").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type='evidence.review-case-created'")
                .query(Long.class).single()).isZero();
    }

    @Test
    void forceApproveRequiresCapabilityReasonsAndApprovalRef() throws Exception {
        EvidenceSetSnapshotView snapshot = createSnapshot("force");
        ReviewCaseView created = reviews.create(reviewer(), metadata("create-force"),
                new CreateReviewCaseCommand(snapshot.evidenceSetSnapshotId(), null));

        assertThatThrownBy(() -> reviews.forceApprove(reviewer(), metadata("force-denied"),
                new ForceApproveReviewCaseCommand(
                        created.reviewCaseId(), List.of("UNMET_OCR"), "APR-1", "no")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));

        assertThatThrownBy(() -> reviews.forceApprove(forceAdmin(), metadata("force-empty"),
                new ForceApproveReviewCaseCommand(created.reviewCaseId(), List.of(), "APR-1", null)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));

        assertThatThrownBy(() -> reviews.forceApprove(forceAdmin(), metadata("force-no-ref"),
                new ForceApproveReviewCaseCommand(
                        created.reviewCaseId(), List.of("UNMET_OCR"), "  ", null)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));

        ReviewCaseView forced = reviews.forceApprove(forceAdmin(), metadata("force-ok"),
                new ForceApproveReviewCaseCommand(
                        created.reviewCaseId(), List.of("UNMET_OCR"), "APR-FORCE-1", "override"));
        ReviewCaseView replay = reviews.forceApprove(forceAdmin(), metadata("force-ok"),
                new ForceApproveReviewCaseCommand(
                        created.reviewCaseId(), List.of("UNMET_OCR"), "APR-FORCE-1", "override"));

        assertThat(replay.reviewCaseId()).isEqualTo(forced.reviewCaseId());
        assertThat(forced.status()).isEqualTo("FORCE_APPROVED");
        assertThat(forced.decisions()).hasSize(1);
        assertThat(forced.decisions().getFirst().decision()).isEqualTo("FORCE_APPROVED");
        assertThat(forced.decisions().getFirst().approvalRef()).isEqualTo("APR-FORCE-1");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_outbox_event
                 WHERE event_type='evidence.review-decided'
                   AND payload::text LIKE '%FORCE_APPROVED%'
                """).query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM evd_correction_case").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM aud_audit_record WHERE action_name='REVIEW_CASE_FORCE_APPROVED'")
                .query(Long.class).single()).isOne();
    }

    @Test
    void reopenCreatesSuccessorOpenCaseAndPreservesPriorDecisions() throws Exception {
        EvidenceSetSnapshotView snapshot = createSnapshot("reopen");
        ReviewCaseView created = reviews.create(reviewer(), metadata("create-reopen"),
                new CreateReviewCaseCommand(snapshot.evidenceSetSnapshotId(), null));
        var approvedResult = reviews.decide(reviewer(), metadata("approve-reopen"),
                decideCommand(created.reviewCaseId(), "APPROVED", List.of(), "ok"));
        ReviewCaseView approved = approvedResult.reviewCase();

        assertThatThrownBy(() -> reviews.reopen(reviewer(), metadata("reopen-denied"),
                new ReopenReviewCaseCommand(approved.reviewCaseId(), "oem reject", "OEM-1", null)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));

        ReviewCaseView reopened = reviews.reopen(forceAdmin(), metadata("reopen-ok"),
                new ReopenReviewCaseCommand(
                        approved.reviewCaseId(),
                        "OEM requested another complete review round after additional verification",
                        "OEM_REJECTION:batch-1", "APR-REOPEN-LONG-REFERENCE-1"));
        ReviewCaseView reopenReplay = reviews.reopen(forceAdmin(), metadata("reopen-ok"),
                new ReopenReviewCaseCommand(
                        approved.reviewCaseId(),
                        "OEM requested another complete review round after additional verification",
                        "OEM_REJECTION:batch-1", "APR-REOPEN-LONG-REFERENCE-1"));

        assertThat(reopenReplay.reviewCaseId()).isEqualTo(reopened.reviewCaseId());
        assertThat(reopened.reviewCaseId()).isNotEqualTo(approved.reviewCaseId());
        assertThat(reopened.status()).isEqualTo("OPEN");
        assertThat(reopened.reopenedFromReviewCaseId()).isEqualTo(approved.reviewCaseId());
        assertThat(reopened.reopenTriggerRef()).isEqualTo("OEM_REJECTION:batch-1");
        assertThat(reopened.decisions()).isEmpty();
        assertThat(jdbc.sql("""
                SELECT result_code FROM aud_audit_record
                 WHERE action_name = 'REVIEW_CASE_REOPENED'
                   AND target_id = :targetId
                """).param("targetId", reopened.reviewCaseId().toString())
                .query(String.class).single()).isEqualTo("REOPENED");

        ReviewCaseView source = reviews.get(forceAdmin(), "corr-get-source", approved.reviewCaseId());
        assertThat(source.status()).isEqualTo("REOPENED");
        assertThat(source.decisions()).hasSize(1);
        assertThat(source.decisions().getFirst().decision()).isEqualTo("APPROVED");

        var decidedAgainResult = reviews.decide(reviewer(), metadata("decide-after-reopen"),
                decideCommand(reopened.reviewCaseId(), "REJECTED", List.of("FAKE_PHOTO"), "again"));
        ReviewCaseView decidedAgain = decidedAgainResult.reviewCase();
        assertThat(decidedAgain.status()).isEqualTo("REJECTED");
        assertThat(jdbc.sql("SELECT count(*) FROM evd_review_case").query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM evd_review_decision").query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type='evidence.review-case-reopened'")
                .query(Long.class).single()).isOne();
    }

    @Test
    void createsClientCaseOnlyFromApprovedInternalCaseAndFreezesExternalLineage() throws Exception {
        EvidenceSetSnapshotView snapshot = createSnapshot("client-origin");
        ReviewCaseView source = reviews.create(reviewer(), metadata("create-client-source"),
                new CreateReviewCaseCommand(snapshot.evidenceSetSnapshotId(), null));
        CreateClientReviewCaseCommand command = new CreateClientReviewCaseCommand(
                source.reviewCaseId(), "SUBMISSION-55", "BATCH-55", "MAP-55", "CLIENT-POLICY-55");

        assertThatThrownBy(() -> reviews.createClient(reviewer(), metadata("client-user"), command))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
        assertThatThrownBy(() -> reviews.createClient(adapter(), metadata("client-before-approval"), command))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.REVIEW_CASE_STATE_CONFLICT));

        var approvedResult = reviews.decide(reviewer(), metadata("approve-client-source"),
                decideCommand(source.reviewCaseId(), "APPROVED", List.of(), "send to client"));
        ReviewCaseView approved = approvedResult.reviewCase();
        CurrentPrincipal ungrantedAdapter = new CurrentPrincipal(
                "ungranted-adapter", TENANT, CurrentPrincipal.PrincipalType.SERVICE,
                "ungranted-adapter", Set.of());
        assertThatThrownBy(() -> reviews.createClient(
                ungrantedAdapter, metadata("client-missing-capability"), command))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
        ReviewCaseView client = reviews.createClient(adapter(), metadata("create-client"), command);
        ReviewCaseView replay = reviews.createClient(adapter(), metadata("create-client"), command);

        assertThat(replay.reviewCaseId()).isEqualTo(client.reviewCaseId());
        assertThat(client.origin()).isEqualTo("CLIENT");
        assertThat(client.sourceReviewCaseId()).isEqualTo(approved.reviewCaseId());
        assertThat(client.evidenceSetSnapshotId()).isEqualTo(snapshot.evidenceSetSnapshotId());
        assertThat(client.snapshotContentDigest()).isEqualTo(snapshot.contentDigest());
        assertThat(client.externalSubmissionRef()).isEqualTo("SUBMISSION-55");
        assertThat(client.callbackBatchRef()).isEqualTo("BATCH-55");
        assertThat(client.mappingVersionId()).isEqualTo("MAP-55");
        assertThat(client.policyVersion()).isEqualTo("CLIENT-POLICY-55");
        assertThatThrownBy(() -> reviews.decide(reviewer(), metadata("client-internal-decision"),
                decideCommand(client.reviewCaseId(), "APPROVED", List.of(), null)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.REVIEW_CASE_STATE_CONFLICT));
        assertThatThrownBy(() -> reviews.forceApprove(forceAdmin(), metadata("client-force-decision"),
                new ForceApproveReviewCaseCommand(
                        client.reviewCaseId(), List.of("CLIENT_OVERRIDE"), "APR-CLIENT", null)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.REVIEW_CASE_STATE_CONFLICT));
        assertThatThrownBy(() -> reviews.reopen(forceAdmin(), metadata("client-reopen"),
                new ReopenReviewCaseCommand(
                        client.reviewCaseId(), "invalid internal reopen", "CLIENT-REOPEN", null)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.REVIEW_CASE_STATE_CONFLICT));
        assertThatThrownBy(() -> reviews.createClient(adapter(), metadata("client-from-client"),
                new CreateClientReviewCaseCommand(
                        client.reviewCaseId(), "SUBMISSION-CLIENT", "BATCH-CLIENT",
                        "MAP-CLIENT", "CLIENT-POLICY-55")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.REVIEW_CASE_STATE_CONFLICT));
        assertThatThrownBy(() -> reviews.createClient(adapter(), metadata("client-same-snapshot"),
                new CreateClientReviewCaseCommand(
                        approved.reviewCaseId(), "SUBMISSION-OTHER", "BATCH-OTHER",
                        "MAP-OTHER", "CLIENT-POLICY-55")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.REVIEW_CASE_CONFLICT));
        assertThatThrownBy(() -> receipts.record(adapter(), metadata("receipt-for-internal"),
                new RecordExternalReviewReceiptCommand(
                        approved.reviewCaseId(), "ENV-INTERNAL", "CAN-INTERNAL", "EXT-INTERNAL",
                        "BATCH-55", "MAP-55", "APPROVED", List.of(), List.of(), null)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.REVIEW_CASE_STATE_CONFLICT));
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_outbox_event
                 WHERE event_type='evidence.client-review-case-created'
                """).query(Long.class).single()).isOne();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name='CLIENT_REVIEW_CASE_CREATED'
                """).query(Long.class).single()).isOne();

        assertThatThrownBy(() -> reviews.createClient(
                adapter(), metadata("client-duplicate"), command))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.REVIEW_CASE_CONFLICT));

        EvidenceSetSnapshotView rejectedSnapshot = createSnapshot("client-rejected-source");
        ReviewCaseView rejectedSource = reviews.create(reviewer(), metadata("create-rejected-source"),
                new CreateReviewCaseCommand(rejectedSnapshot.evidenceSetSnapshotId(), null));
        var rejectedResult = reviews.decide(reviewer(), metadata("reject-client-source"),
                decideCommand(rejectedSource.reviewCaseId(), "REJECTED", List.of("CLIENT.NOT_READY"), null));
        ReviewCaseView rejected = rejectedResult.reviewCase();
        assertThatThrownBy(() -> reviews.createClient(adapter(), metadata("client-from-rejected"),
                new CreateClientReviewCaseCommand(
                        rejected.reviewCaseId(), "SUBMISSION-REJECTED", "BATCH-REJECTED",
                        "MAP-REJECTED", "CLIENT-POLICY-55")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.REVIEW_CASE_STATE_CONFLICT));
    }


    @Test
    void recordsExternalReceiptAndOpensCoordinationTaskOnReject() throws Exception {
        EvidenceSetSnapshotView snapshot = createSnapshot("ext-receipt");
        ReviewCaseView created = createClientCase(snapshot, "ext", "BATCH-1", "MAP-1");

        assertThatThrownBy(() -> receipts.record(reviewer(), metadata("ext-user"),
                new RecordExternalReviewReceiptCommand(
                        created.reviewCaseId(), "ENV-1", "CAN-1", "EXT-1", "BATCH-1", "MAP-1",
                        "REJECTED", List.of("CLIENT.IMAGE.BLUR"), List.of(), null)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));

        ExternalReviewReceiptView recorded = receipts.record(adapter(), metadata("ext-ok"),
                new RecordExternalReviewReceiptCommand(
                        created.reviewCaseId(), "ENV-1", "CAN-1", "EXT-1", "BATCH-1", "MAP-1",
                        "REJECTED", List.of("CLIENT.IMAGE.BLUR"),
                        List.of(target(snapshot)),
                        "PAYLOAD-1"));
        ExternalReviewReceiptView replay = receipts.record(adapter(), metadata("ext-ok"),
                new RecordExternalReviewReceiptCommand(
                        created.reviewCaseId(), "ENV-1", "CAN-1", "EXT-1", "BATCH-1", "MAP-1",
                        "REJECTED", List.of("CLIENT.IMAGE.BLUR"),
                        List.of(target(snapshot)),
                        "PAYLOAD-1"));
        ExternalReviewReceiptView envelopeReplay = receipts.record(adapter(), metadata("ext-env-replay"),
                new RecordExternalReviewReceiptCommand(
                        created.reviewCaseId(), "ENV-1", "CAN-1", "EXT-1", "BATCH-1", "MAP-1",
                        "REJECTED", List.of("CLIENT.IMAGE.BLUR"),
                        List.of(target(snapshot)),
                        "PAYLOAD-1"));

        assertThat(replay.receiptId()).isEqualTo(recorded.receiptId());
        assertThat(envelopeReplay.receiptId()).isEqualTo(recorded.receiptId());
        assertThat(recorded.result()).isEqualTo("REJECTED");
        assertThat(recorded.coordinationTaskId()).isNotNull();
        ReviewCaseView decided = reviews.get(reviewer(), "corr-ext-get", created.reviewCaseId());
        assertThat(decided.status()).isEqualTo("REJECTED");
        assertThat(decided.decisions()).hasSize(1);
        assertThat(decided.decisions().getFirst().decisionSource()).isEqualTo("EXTERNAL");
        assertThat(jdbc.sql("SELECT count(*) FROM evd_correction_case").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("""
                SELECT task_type FROM tsk_task WHERE task_id=:id
                """).param("id", recorded.coordinationTaskId()).query(String.class).single())
                .isEqualTo("evidence.external-coordination");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_outbox_event
                 WHERE event_type='evidence.external-review-receipt-recorded'
                """).query(Long.class).single()).isOne();
    }

    @Test
    void rejectsExternalTargetsOutsideAuthoritativeReviewSnapshotWithoutSideEffects() throws Exception {
        EvidenceSetSnapshotView authoritative = createSnapshot("ext-authoritative");
        EvidenceSetSnapshotView other = createSnapshot("ext-other");
        ReviewCaseView created = createClientCase(
                authoritative, "ext-authoritative", "BATCH-AUTH", "MAP-AUTH");

        assertThatThrownBy(() -> receipts.record(adapter(), metadata("ext-wrong-batch"),
                new RecordExternalReviewReceiptCommand(
                        created.reviewCaseId(), "ENV-B", "CAN-B", "EXT-B", "BATCH-WRONG", "MAP-AUTH",
                        "REJECTED", List.of("CLIENT.WRONG_BATCH"), List.of(target(authoritative)), null)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));

        assertThatThrownBy(() -> receipts.record(adapter(), metadata("ext-cross-snapshot"),
                new RecordExternalReviewReceiptCommand(
                        created.reviewCaseId(), "ENV-X", "CAN-X", "EXT-X", "BATCH-AUTH", "MAP-AUTH",
                        "REJECTED", List.of("CLIENT.WRONG_TARGET"), List.of(target(other)), null)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));

        ExternalReviewAffectedTarget exact = target(authoritative);
        ExternalReviewAffectedTarget mismatchedSlot = new ExternalReviewAffectedTarget(
                exact.targetType(), UUID.randomUUID(), exact.evidenceItemId(), exact.evidenceRevisionId());
        assertThatThrownBy(() -> receipts.record(adapter(), metadata("ext-mismatched-triple"),
                new RecordExternalReviewReceiptCommand(
                        created.reviewCaseId(), "ENV-Y", "CAN-Y", "EXT-Y", "BATCH-AUTH", "MAP-AUTH",
                        "REJECTED", List.of("CLIENT.WRONG_TARGET"), List.of(mismatchedSlot), null)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));

        assertThatThrownBy(() -> receipts.record(adapter(), metadata("ext-duplicate-target"),
                new RecordExternalReviewReceiptCommand(
                        created.reviewCaseId(), "ENV-Z", "CAN-Z", "EXT-Z", "BATCH-AUTH", "MAP-AUTH",
                        "REJECTED", List.of("CLIENT.DUPLICATE_TARGET"), List.of(exact, exact), null)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));

        assertThat(reviews.get(reviewer(), "ext-still-open", created.reviewCaseId()).status()).isEqualTo("OPEN");
        assertThat(jdbc.sql("SELECT count(*) FROM evd_external_review_receipt")
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM evd_review_decision WHERE decision_source='EXTERNAL'")
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM tsk_task WHERE task_type='evidence.external-coordination'")
                .query(Long.class).single()).isZero();
    }

    private CurrentPrincipal adapter() {
        return new CurrentPrincipal(
                ADAPTER, TENANT, CurrentPrincipal.PrincipalType.SERVICE, "byd-adapter", Set.of());
    }

    private CurrentPrincipal opsUser() {
        return new CurrentPrincipal(
                OPS_USER, TENANT, CurrentPrincipal.PrincipalType.USER, "operations", Set.of());
    }

    private BydCpimReviewCallbackResponse receiveReviewCallback(String nonce, Map<String, Object> payload)
            throws Exception {
        LocalDate protocolDate = LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
        String signature = new BydCpimSignatureVerifier(
                CPIM_APP_KEY, CPIM_APP_SECRET, clock, ZoneId.of("Asia/Shanghai"))
                .sign(nonce, protocolDate, payload);
        return reviewCallbacks.receive(
                new BydCpimSignatureHeaders(CPIM_APP_KEY, nonce, protocolDate, signature),
                new tools.jackson.databind.ObjectMapper().writeValueAsBytes(payload), "corr-" + nonce);
    }

    private ReviewCaseView createApprovedInternalWithBydLineage(String marker, String orderCode) throws Exception {
        EvidenceSetSnapshotView snapshot = createSnapshot(marker);
        ReviewCaseView internal = reviews.create(reviewer(), metadata("create-internal-" + marker),
                new CreateReviewCaseCommand(snapshot.evidenceSetSnapshotId(), null));
        var approvedResult = reviews.decide(reviewer(), metadata("approve-internal-" + marker),
                decideCommand(internal.reviewCaseId(), "APPROVED", List.of(), "send to BYD"));
        ReviewCaseView approved = approvedResult.reviewCase();
        UUID workOrderId = jdbc.sql("SELECT work_order_id FROM tsk_task WHERE task_id=:task")
                .param("task", taskId).query(UUID.class).single();
        UUID envelopeId = UUID.randomUUID();
        String digest = Sha256.digest("canonical-" + marker);
        jdbc.sql("""
                INSERT INTO int_inbound_envelope (
                    inbound_envelope_id, tenant_id, project_id, connector_version_id,
                    message_type, transport_dedup_key, external_message_id, received_at,
                    raw_payload_object_ref, raw_payload_digest, canonical_payload_digest,
                    signature_status, processing_status, mapping_version_id, result_code,
                    result_type, result_id, correlation_id, completed_at)
                VALUES (:id, :tenant, :project, 'byd-cpim-v7.3.1', 'CREATE_WORK_ORDER',
                    :dedup, :externalId, now(), :objectRef, :digest, :digest, 'VALID',
                    'COMPLETED', 'byd-ocean-shandong-install-v1', 'ACCEPTED',
                    'WORK_ORDER', :workOrder, :correlation, now())
                """).param("id", envelopeId).param("tenant", TENANT).param("project", projectId)
                .param("dedup", Sha256.digest("transport-" + marker)).param("externalId", "nonce-" + marker)
                .param("objectRef", "test/inbound/" + marker + ".json").param("digest", digest)
                .param("workOrder", workOrderId.toString()).param("correlation", "corr-" + marker).update();
        jdbc.sql("""
                INSERT INTO int_canonical_message (
                    canonical_message_id, tenant_id, project_id, connector_version_id,
                    message_type, business_key, payload_object_ref, payload_digest,
                    mapping_version_id, processing_status, result_code, result_type,
                    result_id, source_envelope_id, created_at, processed_at)
                VALUES (:id, :tenant, :project, 'byd-cpim-v7.3.1', 'CREATE_WORK_ORDER',
                    :businessKey, :objectRef, :digest, 'byd-ocean-shandong-install-v1',
                    'COMPLETED', 'ACCEPTED', 'WORK_ORDER', :workOrder, :envelope, now(), now())
                """).param("id", UUID.randomUUID()).param("tenant", TENANT).param("project", projectId)
                .param("businessKey", "BYD:INSTALL:" + orderCode)
                .param("objectRef", "test/canonical/" + marker + ".json").param("digest", digest)
                .param("workOrder", workOrderId.toString()).param("envelope", envelopeId).update();
        return approved;
    }

    private CurrentPrincipal forceAdmin() {
        return new CurrentPrincipal(
                FORCE_ADMIN, TENANT, CurrentPrincipal.PrincipalType.USER, "ops-web", Set.of());
    }

    private EvidenceSetSnapshotView createSnapshot(String marker) throws Exception {
        UUID revisionId = uploadScanAndValidate(pngBytes(marker), "begin-" + marker, "cmd-" + marker);
        return snapshots.create(technician(), metadata("snap-" + marker),
                new CreateEvidenceSetSnapshotCommand(taskId, "TASK_SUBMISSION", List.of(revisionId)));
    }

    private ExternalReviewAffectedTarget target(EvidenceSetSnapshotView snapshot) {
        var member = snapshot.members().getFirst();
        return new ExternalReviewAffectedTarget(
                "EVIDENCE_REVISION", member.evidenceSlotId(),
                member.evidenceItemId(), member.evidenceRevisionId());
    }

    private ReviewCaseView createClientCase(
            EvidenceSetSnapshotView snapshot, String marker, String callbackBatchRef, String mappingVersionId
    ) {
        ReviewCaseView internal = reviews.create(reviewer(), metadata("create-internal-" + marker),
                new CreateReviewCaseCommand(snapshot.evidenceSetSnapshotId(), null));
        reviews.decide(reviewer(), metadata("approve-internal-" + marker),
                decideCommand(internal.reviewCaseId(), "APPROVED", List.of(), "send"));
        return reviews.createClient(adapter(), metadata("create-client-" + marker),
                new CreateClientReviewCaseCommand(
                        internal.reviewCaseId(), "SUB-" + marker,
                        callbackBatchRef, mappingVersionId, "CLIENT-POLICY-1"));
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
        UUID revisionId = item.revisions().getFirst().evidenceRevisionId();
        assertThat(jdbc.sql("""
                SELECT status FROM evd_evidence_revision
                 WHERE evidence_revision_id=:id
                """).param("id", revisionId).query(String.class).single()).isEqualTo("VALIDATED");
        return revisionId;
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
        // M331：提审创建强制 OUTBOUND Mapping；夹具对齐原 BYD Profile 硬编码字段。
        String outbound = """
                {"mappingKey":"byd-submit-review","version":"1.0.0","connectorCode":"BYD_CPIM",
                 "direction":"OUTBOUND","fieldMappings":[
                   {"mappingId":"operator","internalPath":"operator","externalPath":"operatePerson","required":true,"transform":"TRIM"},
                   {"mappingId":"order","internalPath":"externalOrderCode","externalPath":"orderCode","required":true,"transform":"NONE"},
                   {"mappingId":"commit","internalPath":"commitDate","externalPath":"commitDate","required":true,"transform":"NONE"}]}
                """.replaceAll("\\s+", "");
        UUID outboundId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.INTEGRATION, "byd-submit-review",
                "1.0.0", "1.0.0", outbound, Sha256.digest(outbound))).versionId();
        ConfigurationBundleReference bundle = configurations.publishBundle(
                new PublishConfigurationBundleCommand(
                        TENANT, projectId, "EVD-REV-BUNDLE", "1.0.0", "BYD", "HOME",
                        null, Instant.now().minusSeconds(60), null, List.of(assetId, outboundId)));
        taskId = UUID.randomUUID();
        workOrderId = UUID.randomUUID();
        // OutboundDelivery.sourceWorkOrderId 指向此工单；时间线投影经 WorkOrderScopeQuery 解析时必须存在。
        jdbc.sql("""
                INSERT INTO wo_work_order (
                    id, tenant_id, project_id, client_code, brand_code, service_product_code,
                    external_order_code, payload_digest, status, configuration_bundle_id,
                    configuration_bundle_code, configuration_bundle_version,
                    configuration_bundle_digest, province_code, city_code, district_code,
                    customer_name, customer_mobile, service_address, vehicle_vin,
                    external_dispatched_at, received_at, version)
                VALUES (
                    :id, :tenantId, :projectId, 'BYD', 'BYD_OCEAN', 'HOME_CHARGING',
                    :externalOrderCode, :payloadDigest, 'RECEIVED', :bundleId,
                    'EVD-REV-BUNDLE', '1.0.0', :bundleDigest, '370000', '370100', '370102',
                    '测试用户', '13800000000', '测试地址', 'LSREV123456789',
                    :dispatchedAt, :receivedAt, 1)
                """)
                .param("id", workOrderId).param("tenantId", TENANT).param("projectId", projectId)
                .param("externalOrderCode", "REV-" + workOrderId)
                .param("payloadDigest", Sha256.digest(workOrderId.toString()))
                .param("bundleId", bundle.bundleId()).param("bundleDigest", bundle.manifestDigest())
                .param("dispatchedAt", java.time.LocalDateTime.now().minusHours(1))
                .param("receivedAt", java.time.OffsetDateTime.now().minusHours(1))
                .update();
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
                    100, 'RUNNING', now(), 0, 1, 'corr-evd-044', 3, now(), now(),
                    :actor, now(), now(), :project, :workOrder, :workflow, :stage,
                    :nodeInstance, 'SITE_SURVEY', :definitionId, :digest, :bundle, :bundleDigest,
                    'SURVEY')
                """).param("task", taskId).param("tenant", TENANT).param("businessKey", taskId.toString())
                .param("digest", "d".repeat(64)).param("actor", TECHNICIAN)
                .param("project", projectId).param("workOrder", workOrderId)
                .param("workflow", UUID.randomUUID()).param("stage", UUID.randomUUID())
                .param("nodeInstance", UUID.randomUUID()).param("definitionId", assetId)
                .param("bundle", bundle.bundleId()).param("bundleDigest", bundle.manifestDigest())
                .update();
        jdbc.sql("""
                INSERT INTO tsk_task_assignment (
                    task_assignment_id, tenant_id, task_id, assignment_kind, principal_type,
                    principal_id, status, source_type, source_id, effective_from, created_by, created_at)
                VALUES (:id, :tenant, :task, 'RESPONSIBLE', 'USER', :actor, 'ACTIVE',
                    'MANUAL', 'M44-FIXTURE', now(), 'fixture', now())
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
        return latestEvent("file.scan-completed");
    }

    private OutboxMessage latestEvent(String eventType) {
        Map<String, Object> row = jdbc.sql("""
                SELECT outbox_id, event_id, module_name, event_type, schema_version,
                       aggregate_type, aggregate_id, aggregate_version, tenant_id,
                       correlation_id, causation_id, partition_key, payload::text AS payload,
                       payload_digest, occurred_at
                  FROM rel_outbox_event WHERE event_type=:eventType
                 ORDER BY occurred_at DESC LIMIT 1
                """).param("eventType", eventType).query().singleRow();
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
        grantOnProject(principalId, projectId, capabilities);
    }

    private void grantOnProject(String principalId, UUID project, String... capabilities) {
        grantOnProject(principalId, project, true, capabilities);
    }

    private void grantOnProject(
            String principalId, UUID project, boolean includeTenantScope, String... capabilities
    ) {
        UUID role = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:role, :tenant, :code, '资料审核角色', 'ACTIVE', now())
                """).param("role", role).param("tenant", TENANT)
                .param("code", "review-role-" + role).update();
        for (String capability : capabilities) {
            jdbc.sql("INSERT INTO auth_role_capability (role_id, capability_code, granted_at) VALUES (:role,:cap,now())")
                    .param("role", role).param("cap", capability).update();
        }
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at)
                VALUES (:grant, :tenant, :principal, :role, 'PROJECT', :project,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M44-REVIEW', now())
                """).param("grant", UUID.randomUUID()).param("tenant", TENANT)
                .param("principal", principalId).param("role", role)
                .param("project", project.toString()).update();
        if (includeTenantScope) {
            jdbc.sql("""
                    INSERT INTO auth_role_grant (
                        grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                        valid_from, source_code, approval_ref, created_at)
                    VALUES (:grant, :tenant, :principal, :role, 'TENANT', :tenant,
                        now() - interval '1 day', 'TEST_FIXTURE', 'M44-REVIEW', now())
                    """).param("grant", UUID.randomUUID()).param("tenant", TENANT)
                    .param("principal", principalId).param("role", role).update();
        }
    }

    private CurrentPrincipal technician() {
        return new CurrentPrincipal(
                TECHNICIAN, TENANT, CurrentPrincipal.PrincipalType.USER, "mobile", Set.of());
    }

    private CurrentPrincipal reviewer() {
        return new CurrentPrincipal(
                REVIEWER, TENANT, CurrentPrincipal.PrincipalType.USER, "ops-web", Set.of());
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
            return Files.createTempDirectory("serviceos-evidence-review-it");
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
                    // best-effort cleanup between tests
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
