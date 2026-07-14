package com.serviceos.files.infrastructure;

import com.serviceos.files.application.FileLifecycleStore;
import com.serviceos.files.application.FinalizationReservation;
import com.serviceos.files.application.StoredFileRecord;
import com.serviceos.files.application.UploadSessionRecord;
import com.serviceos.files.spi.ObjectMetadata;
import com.serviceos.files.spi.ScanOutcome;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * PostgreSQL 文件事实存储。所有查询都带 tenant_id，避免仅凭全局 UUID 穿透租户边界。
 */
@Repository
final class JdbcFileLifecycleStore implements FileLifecycleStore {
    private final JdbcClient jdbc;

    JdbcFileLifecycleStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertSession(UploadSessionRecord session) {
        jdbc.sql("""
                        INSERT INTO fil_upload_session (
                            upload_session_id, file_id, tenant_id, object_key,
                            business_context_type, business_context_id, original_file_name,
                            declared_mime_type, expected_size, expected_sha256, status,
                            expires_at, created_by, created_at, updated_at
                        ) VALUES (
                            :sessionId, :fileId, :tenantId, :objectKey,
                            :contextType, :contextId, :fileName,
                            :mimeType, :size, :sha256, :status,
                            :expiresAt, :createdBy, :createdAt, :createdAt
                        )
                        """)
                .param("sessionId", session.sessionId())
                .param("fileId", session.fileId())
                .param("tenantId", session.tenantId())
                .param("objectKey", session.objectKey())
                .param("contextType", session.businessContextType())
                .param("contextId", session.businessContextId())
                .param("fileName", session.originalFileName())
                .param("mimeType", session.declaredMimeType())
                .param("size", session.expectedSize())
                .param("sha256", session.expectedSha256())
                .param("status", session.status())
                .param("expiresAt", timestamptz(session.expiresAt()))
                .param("createdBy", session.createdBy())
                .param("createdAt", timestamptz(session.createdAt()))
                .update();
    }

    @Override
    public Optional<UploadSessionRecord> findSession(String tenantId, UUID sessionId) {
        return sessionQuery(false, tenantId, sessionId);
    }

    @Override
    public FinalizationReservation reserveFinalization(
            String tenantId,
            UUID sessionId,
            String actualSha256,
            String finalizeCommandId,
            String requestDigest,
            Instant now,
            Duration lease
    ) {
        UploadSessionRecord session = sessionQuery(true, tenantId, sessionId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Upload session was not found"));

        if (!session.expectedSha256().equals(actualSha256)) {
            throw new BusinessProblem(ProblemCode.FILE_OBJECT_MISMATCH,
                    "Finalize checksum does not match the upload session");
        }
        if ("COMPLETED".equals(session.status())) {
            return FinalizationReservation.replay(session);
        }
        if ("FAILED".equals(session.status()) || "EXPIRED".equals(session.status())) {
            ProblemCode code = "EXPIRED".equals(session.status())
                    ? ProblemCode.FILE_UPLOAD_EXPIRED : ProblemCode.FILE_UPLOAD_CONFLICT;
            throw new BusinessProblem(code, "Upload session is no longer finalizable");
        }
        if (!now.isBefore(session.expiresAt())) {
            jdbc.sql("""
                            UPDATE fil_upload_session
                               SET status = 'EXPIRED', finalization_token = NULL,
                                   finalizing_started_at = NULL, updated_at = :now
                             WHERE tenant_id = :tenantId AND upload_session_id = :sessionId
                            """)
                    .param("now", timestamptz(now))
                    .param("tenantId", tenantId)
                    .param("sessionId", sessionId)
                    .update();
            return FinalizationReservation.expired(session);
        }

        if (session.finalizeRequestDigest() != null
                && !requestDigest.equals(session.finalizeRequestDigest())) {
            throw new BusinessProblem(ProblemCode.FILE_UPLOAD_CONFLICT,
                    "Upload session was already finalized with a different request");
        }

        if ("FINALIZING".equals(session.status())) {
            Instant staleBefore = now.minus(lease);
            if (session.finalizingStartedAt() != null && session.finalizingStartedAt().isAfter(staleBefore)) {
                throw new BusinessProblem(ProblemCode.FILE_FINALIZE_IN_PROGRESS,
                        "Upload finalization is already in progress");
            }
        }

        UUID token = UUID.randomUUID();
        int updated = jdbc.sql("""
                        UPDATE fil_upload_session
                           SET status = 'FINALIZING', finalize_request_digest = :requestDigest,
                               finalize_command_id = :commandId, finalization_token = :token,
                               finalizing_started_at = :now, failure_code = NULL, updated_at = :now
                         WHERE tenant_id = :tenantId AND upload_session_id = :sessionId
                           AND status IN ('CREATED', 'UPLOADING', 'FINALIZING')
                        """)
                .param("requestDigest", requestDigest)
                .param("commandId", finalizeCommandId)
                .param("token", token)
                .param("now", timestamptz(now))
                .param("tenantId", tenantId)
                .param("sessionId", sessionId)
                .update();
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.FILE_UPLOAD_CONFLICT,
                    "Upload session changed while finalization was reserved");
        }
        UploadSessionRecord reserved = sessionQuery(false, tenantId, sessionId).orElseThrow();
        return FinalizationReservation.reserved(reserved, token);
    }

    @Override
    public StoredFileRecord completeFinalization(
            UploadSessionRecord session,
            UUID token,
            ObjectMetadata metadata,
            Instant now
    ) {
        int inserted = jdbc.sql("""
                        INSERT INTO fil_stored_file (
                            file_id, tenant_id, upload_session_id, object_key, original_file_name,
                            checksum_sha256, size_bytes, declared_mime_type, detected_mime_type,
                            lifecycle_status, quarantine_reason, created_by, created_at, updated_at, version
                        )
                        SELECT file_id, tenant_id, upload_session_id, object_key, original_file_name,
                               :sha256, :size, declared_mime_type, :detectedMime,
                               'QUARANTINED', 'PENDING_SCAN', created_by, :now, :now, 1
                          FROM fil_upload_session
                         WHERE tenant_id = :tenantId AND upload_session_id = :sessionId
                           AND status = 'FINALIZING' AND finalization_token = :token
                        ON CONFLICT (upload_session_id) DO NOTHING
                        """)
                .param("sha256", metadata.checksumSha256())
                .param("size", metadata.size())
                .param("detectedMime", metadata.detectedMimeType())
                .param("now", timestamptz(now))
                .param("tenantId", session.tenantId())
                .param("sessionId", session.sessionId())
                .param("token", token)
                .update();

        int completed = jdbc.sql("""
                        UPDATE fil_upload_session
                           SET status = 'COMPLETED', finalization_token = NULL,
                               finalizing_started_at = NULL, completed_at = :now, updated_at = :now
                         WHERE tenant_id = :tenantId AND upload_session_id = :sessionId
                           AND status = 'FINALIZING' AND finalization_token = :token
                        """)
                .param("now", timestamptz(now))
                .param("tenantId", session.tenantId())
                .param("sessionId", session.sessionId())
                .param("token", token)
                .update();
        if (completed != 1 || (inserted != 1 && findFile(session.tenantId(), session.fileId()).isEmpty())) {
            throw new BusinessProblem(ProblemCode.FILE_UPLOAD_CONFLICT,
                    "Upload finalization lease was lost");
        }
        return findFile(session.tenantId(), session.fileId()).orElseThrow();
    }

    @Override
    public void failFinalization(String tenantId, UUID sessionId, UUID token, String errorCode, Instant now) {
        jdbc.sql("""
                        UPDATE fil_upload_session
                           SET status = 'FAILED', failure_code = :errorCode,
                               finalization_token = NULL, finalizing_started_at = NULL, updated_at = :now
                         WHERE tenant_id = :tenantId AND upload_session_id = :sessionId
                           AND status = 'FINALIZING' AND finalization_token = :token
                        """)
                .param("errorCode", errorCode)
                .param("now", timestamptz(now))
                .param("tenantId", tenantId)
                .param("sessionId", sessionId)
                .param("token", token)
                .update();
    }

    @Override
    public void releaseFinalization(String tenantId, UUID sessionId, UUID token, String errorCode, Instant now) {
        jdbc.sql("""
                        UPDATE fil_upload_session
                           SET status = 'CREATED', failure_code = :errorCode,
                               finalization_token = NULL, finalizing_started_at = NULL, updated_at = :now
                         WHERE tenant_id = :tenantId AND upload_session_id = :sessionId
                           AND status = 'FINALIZING' AND finalization_token = :token
                        """)
                .param("errorCode", errorCode)
                .param("now", timestamptz(now))
                .param("tenantId", tenantId)
                .param("sessionId", sessionId)
                .param("token", token)
                .update();
    }

    @Override
    public Optional<StoredFileRecord> findFile(String tenantId, UUID fileId) {
        return jdbc.sql("""
                        SELECT file_id, tenant_id, upload_session_id, object_key, original_file_name,
                               checksum_sha256, size_bytes, declared_mime_type, detected_mime_type,
                               lifecycle_status, quarantine_reason, created_at, version
                          FROM fil_stored_file
                         WHERE tenant_id = :tenantId AND file_id = :fileId
                        """)
                .param("tenantId", tenantId)
                .param("fileId", fileId)
                .query(JdbcFileLifecycleStore::mapFile)
                .optional();
    }

    @Override
    @Transactional
    public StoredFileRecord recordScanResult(
            String tenantId,
            UUID fileId,
            UUID taskId,
            UUID attemptId,
            ScanOutcome outcome,
            Instant now
    ) {
        StoredFileRecord current = findFile(tenantId, fileId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "File was not found"));
        if ("AVAILABLE".equals(current.lifecycleStatus()) || "MALWARE".equals(current.quarantineReason())) {
            return current;
        }
        if (!"QUARANTINED".equals(current.lifecycleStatus())
                || !"PENDING_SCAN".equals(current.quarantineReason())) {
            throw new BusinessProblem(ProblemCode.FILE_UPLOAD_CONFLICT,
                    "File is not awaiting a content scan");
        }

        jdbc.sql("""
                        INSERT INTO fil_scan_result (
                            scan_result_id, tenant_id, file_id, task_id, attempt_id,
                            result_code, scanner_name, scanner_version, reason_code, scanned_at
                        ) VALUES (
                            :scanId, :tenantId, :fileId, :taskId, :attemptId,
                            :result, :scannerName, :scannerVersion, :reasonCode, :now
                        )
                        ON CONFLICT (file_id, attempt_id) DO NOTHING
                        """)
                .param("scanId", UUID.randomUUID())
                .param("tenantId", tenantId)
                .param("fileId", fileId)
                .param("taskId", taskId)
                .param("attemptId", attemptId)
                .param("result", outcome.result().name())
                .param("scannerName", outcome.scannerName())
                .param("scannerVersion", outcome.scannerVersion())
                .param("reasonCode", outcome.reasonCode(), java.sql.Types.VARCHAR)
                .param("now", timestamptz(now))
                .update();

        String status = outcome.result() == ScanOutcome.Result.CLEAN ? "AVAILABLE" : "QUARANTINED";
        String reason = outcome.result() == ScanOutcome.Result.CLEAN ? null : "MALWARE";
        jdbc.sql("""
                        UPDATE fil_stored_file
                           SET lifecycle_status = :status, quarantine_reason = :reason,
                               updated_at = :now, version = version + 1
                         WHERE tenant_id = :tenantId AND file_id = :fileId
                           AND lifecycle_status = 'QUARANTINED' AND quarantine_reason = 'PENDING_SCAN'
                        """)
                .param("status", status)
                .param("reason", reason, java.sql.Types.VARCHAR)
                .param("now", timestamptz(now))
                .param("tenantId", tenantId)
                .param("fileId", fileId)
                .update();
        return findFile(tenantId, fileId).orElseThrow();
    }

    @Override
    public void insertDownloadAuthorization(
            UUID authorizationId,
            StoredFileRecord file,
            String principalId,
            String purpose,
            String correlationId,
            Instant issuedAt,
            Instant expiresAt
    ) {
        jdbc.sql("""
                        INSERT INTO fil_download_authorization (
                            authorization_id, tenant_id, file_id, principal_id,
                            purpose, correlation_id, issued_at, expires_at
                        ) VALUES (
                            :authorizationId, :tenantId, :fileId, :principalId,
                            :purpose, :correlationId, :issuedAt, :expiresAt
                        )
                        """)
                .param("authorizationId", authorizationId)
                .param("tenantId", file.tenantId())
                .param("fileId", file.fileId())
                .param("principalId", principalId)
                .param("purpose", purpose)
                .param("correlationId", correlationId)
                .param("issuedAt", timestamptz(issuedAt))
                .param("expiresAt", timestamptz(expiresAt))
                .update();
    }

    private Optional<UploadSessionRecord> sessionQuery(boolean forUpdate, String tenantId, UUID sessionId) {
        String lock = forUpdate ? " FOR UPDATE" : "";
        // lock 片段是内部常量，不接受外部输入；所有值仍使用绑定参数。
        return jdbc.sql("""
                        SELECT upload_session_id, file_id, tenant_id, object_key,
                               business_context_type, business_context_id, original_file_name,
                               declared_mime_type, expected_size, expected_sha256, status,
                               expires_at, finalize_request_digest, finalize_command_id,
                               finalization_token, finalizing_started_at, created_by, created_at
                          FROM fil_upload_session
                         WHERE tenant_id = :tenantId AND upload_session_id = :sessionId
                        """ + lock)
                .param("tenantId", tenantId)
                .param("sessionId", sessionId)
                .query(JdbcFileLifecycleStore::mapSession)
                .optional();
    }

    private static UploadSessionRecord mapSession(ResultSet rs, int rowNum) throws SQLException {
        return new UploadSessionRecord(
                rs.getObject("upload_session_id", UUID.class),
                rs.getObject("file_id", UUID.class),
                rs.getString("tenant_id"),
                rs.getString("object_key"),
                rs.getString("business_context_type"),
                rs.getString("business_context_id"),
                rs.getString("original_file_name"),
                rs.getString("declared_mime_type"),
                rs.getLong("expected_size"),
                rs.getString("expected_sha256"),
                rs.getString("status"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getString("finalize_request_digest"),
                rs.getString("finalize_command_id"),
                rs.getObject("finalization_token", UUID.class),
                rs.getTimestamp("finalizing_started_at") == null
                        ? null : rs.getTimestamp("finalizing_started_at").toInstant(),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant());
    }


    @Override
    public int invalidateFile(String tenantId, UUID fileId, String expectedStatus, Instant now) {
        return jdbc.sql("""
                UPDATE fil_stored_file
                   SET lifecycle_status = 'INVALIDATED',
                       updated_at = :now,
                       version = version + 1
                 WHERE tenant_id = :tenantId
                   AND file_id = :fileId
                   AND lifecycle_status = :expectedStatus
                """)
                .param("now", timestamptz(now))
                .param("tenantId", tenantId)
                .param("fileId", fileId)
                .param("expectedStatus", expectedStatus)
                .update();
    }

    private static StoredFileRecord mapFile(ResultSet rs, int rowNum) throws SQLException {
        return new StoredFileRecord(
                rs.getObject("file_id", UUID.class),
                rs.getString("tenant_id"),
                rs.getObject("upload_session_id", UUID.class),
                rs.getString("object_key"),
                rs.getString("original_file_name"),
                rs.getString("checksum_sha256"),
                rs.getLong("size_bytes"),
                rs.getString("declared_mime_type"),
                rs.getString("detected_mime_type"),
                rs.getString("lifecycle_status"),
                rs.getString("quarantine_reason"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getLong("version"));
    }
}
