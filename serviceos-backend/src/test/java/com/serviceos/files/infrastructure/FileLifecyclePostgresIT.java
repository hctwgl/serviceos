package com.serviceos.files.infrastructure;

import com.serviceos.ServiceOsApplication;
import com.serviceos.files.api.AuthorizeDownloadCommand;
import com.serviceos.files.api.BeginUploadCommand;
import com.serviceos.files.api.FileCommandService;
import com.serviceos.files.api.FinalizeUploadCommand;
import com.serviceos.files.api.StoredFileView;
import com.serviceos.files.api.UploadSessionView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.application.TaskExecutionWorker;
import org.flywaydb.core.Flyway;
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
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FILE-001 PostgreSQL 纵向闭环：Begin → 私有直传 → Finalize → 隔离 → Scan → 授权下载。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FileLifecyclePostgresIT {
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
                () -> "integration-test-signing-key-with-at-least-thirty-two-bytes");
        registry.add("serviceos.task.scheduling-enabled", () -> "false");
    }

    @Autowired
    FileCommandService files;

    @Autowired
    LocalObjectTransferService transfers;

    @Autowired
    TaskExecutionWorker worker;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Flyway flyway;

    @BeforeEach
    void cleanAndSeedAuthorization() throws Exception {
        jdbc.sql("""
                        TRUNCATE TABLE
                            fil_download_authorization, fil_scan_result, fil_stored_file, fil_upload_session,
                            tsk_task_reassignment_command_result,
                            tsk_task_execution_guard, tsk_task_assignment, tsk_task_assignment_batch,
                            tsk_human_task_command_result, tsk_task_execution_attempt, tsk_task,
                            aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                            rel_idempotency_record,
                            auth_role_field_policy, auth_role_grant,
                            auth_role_capability, auth_role CASCADE
                        """).update();
        deleteRecursively(STORAGE_ROOT);
        Files.createDirectories(STORAGE_ROOT);
        seedFileRole("file-actor");
    }

    @Test
    void cleanFileCompletesIdempotentlyThenBecomesDownloadableOnlyAfterScan() throws Exception {
        byte[] content = pngBytes("safe-image");
        String checksum = sha256(content);
        CommandMetadata beginMetadata = metadata("clean-begin");
        BeginUploadCommand beginCommand = new BeginUploadCommand(
                "Task", "task-clean-1", "nameplate.png", "image/png", content.length, checksum);

        UploadSessionView first = files.beginUpload(principal(), beginMetadata, beginCommand);
        UploadSessionView replay = files.beginUpload(principal(), beginMetadata, beginCommand);
        assertThat(replay.uploadSessionId()).isEqualTo(first.uploadSessionId());
        assertThat(count("fil_upload_session")).isEqualTo(1);
        assertThat(count("rel_idempotency_record")).isEqualTo(1);

        transfers.upload(
                token(first.uploadUrl()), "image/png", content.length, new ByteArrayInputStream(content));
        StoredFileView finalized = files.finalizeUpload(
                principal(), metadata("clean-finalize"), first.uploadSessionId(),
                new FinalizeUploadCommand(checksum, "device-command-clean-1"));
        StoredFileView finalizeReplay = files.finalizeUpload(
                principal(), metadata("clean-finalize-replay"), first.uploadSessionId(),
                new FinalizeUploadCommand(checksum, "device-command-clean-1"));

        assertThat(finalized.lifecycleStatus()).isEqualTo("QUARANTINED");
        assertThat(finalized.quarantineReason()).isEqualTo("PENDING_SCAN");
        assertThat(finalizeReplay.fileId()).isEqualTo(finalized.fileId());
        assertThat(count("fil_stored_file")).isEqualTo(1);
        assertThat(count("tsk_task")).isEqualTo(1);
        assertThatThrownBy(() -> files.authorizeDownload(
                principal(), "corr-before-scan", finalized.fileId(), new AuthorizeDownloadCommand("审核原图")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.FILE_NOT_AVAILABLE));

        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        assertThat(jdbc.sql("SELECT lifecycle_status FROM fil_stored_file")
                .query(String.class).single()).isEqualTo("AVAILABLE");
        assertThat(count("fil_scan_result")).isEqualTo(1);
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM rel_outbox_event
                         WHERE event_type = 'file.scan-completed'
                        """).query(Long.class).single()).isEqualTo(1);

        var authorization = files.authorizeDownload(
                principal(), "corr-download-clean", finalized.fileId(),
                new AuthorizeDownloadCommand("总部资料审核"));
        try (var downloaded = transfers.download(token(authorization.downloadUrl())).content()) {
            assertThat(downloaded.readAllBytes()).isEqualTo(content);
        }
        assertThat(count("fil_download_authorization")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT purpose FROM fil_download_authorization")
                .query(String.class).single()).isEqualTo("总部资料审核");
    }

    @Test
    void maliciousContentRemainsQuarantinedAndCannotReceiveDownloadGrant() throws Exception {
        byte[] content = "prefix-EICAR-STANDARD-ANTIVIRUS-TEST-FILE-suffix"
                .getBytes(StandardCharsets.US_ASCII);
        String checksum = sha256(content);
        UploadSessionView session = files.beginUpload(
                principal(), metadata("malicious-begin"),
                new BeginUploadCommand(
                        "Task", "task-malicious-1", "note.txt", "text/plain", content.length, checksum));
        transfers.upload(token(session.uploadUrl()), "text/plain", content.length, new ByteArrayInputStream(content));
        StoredFileView file = files.finalizeUpload(
                principal(), metadata("malicious-finalize"), session.uploadSessionId(),
                new FinalizeUploadCommand(checksum, "device-command-malicious-1"));

        assertThat(worker.runOnce()).isEqualTo(TaskExecutionWorker.RunResult.SUCCEEDED);
        assertThat(jdbc.sql("SELECT quarantine_reason FROM fil_stored_file WHERE file_id = :fileId")
                .param("fileId", file.fileId()).query(String.class).single()).isEqualTo("MALWARE");
        assertThat(jdbc.sql("SELECT result_code FROM fil_scan_result WHERE file_id = :fileId")
                .param("fileId", file.fileId()).query(String.class).single()).isEqualTo("MALICIOUS");
        assertThatThrownBy(() -> files.authorizeDownload(
                principal(), "corr-download-malicious", file.fileId(),
                new AuthorizeDownloadCommand("错误下载尝试")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.FILE_NOT_AVAILABLE));
        assertThat(count("fil_download_authorization")).isZero();
    }

    @Test
    void fakeMimeFailsFinalizeWithoutCreatingFileOrScanTask() throws Exception {
        byte[] content = "%PDF-1.7 fake-pdf".getBytes(StandardCharsets.US_ASCII);
        String checksum = sha256(content);
        UploadSessionView session = files.beginUpload(
                principal(), metadata("fake-mime-begin"),
                new BeginUploadCommand(
                        "Task", "task-fake-1", "fake.jpg", "image/jpeg", content.length, checksum));
        transfers.upload(token(session.uploadUrl()), "image/jpeg", content.length, new ByteArrayInputStream(content));

        assertThatThrownBy(() -> files.finalizeUpload(
                principal(), metadata("fake-mime-finalize"), session.uploadSessionId(),
                new FinalizeUploadCommand(checksum, "device-command-fake-1")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.FILE_OBJECT_MISMATCH));
        assertThat(count("fil_stored_file")).isZero();
        assertThat(count("tsk_task")).isZero();
        assertThat(jdbc.sql("SELECT status FROM fil_upload_session")
                .query(String.class).single()).isEqualTo("FAILED");
    }

    @Test
    void expiredSessionCommitsExpiredStateBeforeReturningGone() throws Exception {
        byte[] content = pngBytes("expired");
        String checksum = sha256(content);
        UploadSessionView session = files.beginUpload(
                principal(), metadata("expired-begin"),
                new BeginUploadCommand(
                        "Task", "task-expired-1", "expired.png", "image/png", content.length, checksum));
        jdbc.sql("""
                        UPDATE fil_upload_session SET expires_at = now() - interval '1 second'
                         WHERE upload_session_id = :sessionId
                        """).param("sessionId", session.uploadSessionId()).update();

        assertThatThrownBy(() -> files.finalizeUpload(
                principal(), metadata("expired-finalize"), session.uploadSessionId(),
                new FinalizeUploadCommand(checksum, "device-command-expired-1")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.FILE_UPLOAD_EXPIRED));
        assertThat(jdbc.sql("SELECT status FROM fil_upload_session")
                .query(String.class).single()).isEqualTo("EXPIRED");
    }

    @Test
    void migrationIsRepeatableNoOpAtCurrentVersion() {
        assertThat(flyway.info().applied()).hasSize(119);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    private CurrentPrincipal principal() {
        return new CurrentPrincipal(
                "file-actor", "tenant-file-test", CurrentPrincipal.PrincipalType.USER,
                "technician-app", Set.of("file.upload", "file.download"));
    }

    private void seedFileRole(String principalId) {
        UUID roleId = UUID.fromString("46127658-e70e-4ae8-8673-513378ee91bb");
        jdbc.sql("""
                        INSERT INTO auth_role (
                            role_id, tenant_id, role_code, role_name, role_status, created_at
                        ) VALUES (
                            :roleId, 'tenant-file-test', 'file-operator', '文件操作员', 'ACTIVE', now()
                        )
                        """).param("roleId", roleId).update();
        jdbc.sql("""
                        INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                        VALUES
                            (:roleId, 'file.upload', now()),
                            (:roleId, 'file.download', now())
                        """).param("roleId", roleId).update();
        jdbc.sql("""
                        INSERT INTO auth_role_grant (
                            grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                            valid_from, source_code, approval_ref, created_at
                        ) VALUES (
                            :grantId, 'tenant-file-test', :principalId, :roleId,
                            'TENANT', 'tenant-file-test', now() - interval '1 day',
                            'TEST_FIXTURE', 'file-test-approval', now()
                        )
                        """)
                .param("grantId", UUID.randomUUID())
                .param("principalId", principalId)
                .param("roleId", roleId)
                .update();
    }

    private static CommandMetadata metadata(String suffix) {
        return new CommandMetadata("corr-" + suffix, "idem-" + suffix);
    }

    private long count(String table) {
        // 表名仅来自测试常量，不接受外部输入。
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private static String token(String url) {
        String path = URI.create(url).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static byte[] pngBytes(String suffix) {
        byte[] tail = suffix.getBytes(StandardCharsets.UTF_8);
        byte[] value = new byte[8 + tail.length];
        byte[] signature = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, value, 0, signature.length);
        System.arraycopy(tail, 0, value, signature.length, tail.length);
        return value;
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("serviceos-file-it-");
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                    .filter(path -> !path.equals(root))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
        }
    }
}
