package com.serviceos.files.infrastructure;

import com.serviceos.files.application.FileLifecycleStore;
import com.serviceos.files.application.FinalizationReservation;
import com.serviceos.files.application.StoredFileRecord;
import com.serviceos.files.application.UploadSessionRecord;
import com.serviceos.files.spi.ObjectMetadata;
import com.serviceos.files.spi.ScanOutcome;
import com.serviceos.jooq.generated.tables.FilDownloadAuthorization;
import com.serviceos.jooq.generated.tables.FilScanResult;
import com.serviceos.jooq.generated.tables.FilStoredFile;
import com.serviceos.jooq.generated.tables.FilUploadSession;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.FilDownloadAuthorization.FIL_DOWNLOAD_AUTHORIZATION;
import static com.serviceos.jooq.generated.tables.FilScanResult.FIL_SCAN_RESULT;
import static com.serviceos.jooq.generated.tables.FilStoredFile.FIL_STORED_FILE;
import static com.serviceos.jooq.generated.tables.FilUploadSession.FIL_UPLOAD_SESSION;

/**
 * PostgreSQL 文件事实存储。所有查询都带 tenant_id，避免仅凭全局 UUID 穿透租户边界。
 */
@Repository
final class JooqFileLifecycleStore implements FileLifecycleStore {
    private final DSLContext dsl;

    JooqFileLifecycleStore(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void insertSession(UploadSessionRecord session) {
        FilUploadSession table = FIL_UPLOAD_SESSION;
        dsl.insertInto(table)
                .set(table.UPLOAD_SESSION_ID, session.sessionId())
                .set(table.FILE_ID, session.fileId())
                .set(table.TENANT_ID, session.tenantId())
                .set(table.OBJECT_KEY, session.objectKey())
                .set(table.BUSINESS_CONTEXT_TYPE, session.businessContextType())
                .set(table.BUSINESS_CONTEXT_ID, session.businessContextId())
                .set(table.ORIGINAL_FILE_NAME, session.originalFileName())
                .set(table.DECLARED_MIME_TYPE, session.declaredMimeType())
                .set(table.EXPECTED_SIZE, session.expectedSize())
                .set(table.EXPECTED_SHA256, session.expectedSha256())
                .set(table.STATUS, session.status())
                .set(table.EXPIRES_AT, session.expiresAt())
                .set(table.CREATED_BY, session.createdBy())
                .set(table.CREATED_AT, session.createdAt())
                .set(table.UPDATED_AT, session.createdAt())
                .execute();
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
            FilUploadSession table = FIL_UPLOAD_SESSION;
            dsl.update(table)
                    .set(table.STATUS, "EXPIRED")
                    .setNull(table.FINALIZATION_TOKEN)
                    .setNull(table.FINALIZING_STARTED_AT)
                    .set(table.UPDATED_AT, now)
                    .where(table.TENANT_ID.eq(tenantId))
                    .and(table.UPLOAD_SESSION_ID.eq(sessionId))
                    .execute();
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
        // 状态条件 UPDATE 防止并发抢占同一 finalize 租约；影响行数必须为 1。
        FilUploadSession table = FIL_UPLOAD_SESSION;
        int updated = dsl.update(table)
                .set(table.STATUS, "FINALIZING")
                .set(table.FINALIZE_REQUEST_DIGEST, requestDigest)
                .set(table.FINALIZE_COMMAND_ID, finalizeCommandId)
                .set(table.FINALIZATION_TOKEN, token)
                .set(table.FINALIZING_STARTED_AT, now)
                .setNull(table.FAILURE_CODE)
                .set(table.UPDATED_AT, now)
                .where(table.TENANT_ID.eq(tenantId))
                .and(table.UPLOAD_SESSION_ID.eq(sessionId))
                .and(table.STATUS.in("CREATED", "UPLOADING", "FINALIZING"))
                .execute();
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
        FilStoredFile file = FIL_STORED_FILE;
        FilUploadSession uploadSession = FIL_UPLOAD_SESSION;
        // INSERT...SELECT 把会话事实落成不可变文件；字面量用 DSL.val(value, 目标字段) 保持类型对齐。
        int inserted = dsl.insertInto(file)
                .columns(
                        file.FILE_ID, file.TENANT_ID, file.UPLOAD_SESSION_ID, file.OBJECT_KEY,
                        file.ORIGINAL_FILE_NAME, file.CHECKSUM_SHA256, file.SIZE_BYTES,
                        file.DECLARED_MIME_TYPE, file.DETECTED_MIME_TYPE, file.LIFECYCLE_STATUS,
                        file.QUARANTINE_REASON, file.CREATED_BY, file.CREATED_AT, file.UPDATED_AT,
                        file.VERSION)
                .select(dsl.select(
                                uploadSession.FILE_ID, uploadSession.TENANT_ID,
                                uploadSession.UPLOAD_SESSION_ID, uploadSession.OBJECT_KEY,
                                uploadSession.ORIGINAL_FILE_NAME,
                                DSL.val(metadata.checksumSha256(), file.CHECKSUM_SHA256),
                                DSL.val(metadata.size(), file.SIZE_BYTES),
                                uploadSession.DECLARED_MIME_TYPE,
                                DSL.val(metadata.detectedMimeType(), file.DETECTED_MIME_TYPE),
                                DSL.val("QUARANTINED", file.LIFECYCLE_STATUS),
                                DSL.val("PENDING_SCAN", file.QUARANTINE_REASON),
                                uploadSession.CREATED_BY,
                                DSL.val(now, file.CREATED_AT),
                                DSL.val(now, file.UPDATED_AT),
                                DSL.val(1L, file.VERSION))
                        .from(uploadSession)
                        .where(uploadSession.TENANT_ID.eq(session.tenantId()))
                        .and(uploadSession.UPLOAD_SESSION_ID.eq(session.sessionId()))
                        .and(uploadSession.STATUS.eq("FINALIZING"))
                        .and(uploadSession.FINALIZATION_TOKEN.eq(token)))
                .onConflict(file.UPLOAD_SESSION_ID)
                .doNothing()
                .execute();

        int completed = dsl.update(uploadSession)
                .set(uploadSession.STATUS, "COMPLETED")
                .setNull(uploadSession.FINALIZATION_TOKEN)
                .setNull(uploadSession.FINALIZING_STARTED_AT)
                .set(uploadSession.COMPLETED_AT, now)
                .set(uploadSession.UPDATED_AT, now)
                .where(uploadSession.TENANT_ID.eq(session.tenantId()))
                .and(uploadSession.UPLOAD_SESSION_ID.eq(session.sessionId()))
                .and(uploadSession.STATUS.eq("FINALIZING"))
                .and(uploadSession.FINALIZATION_TOKEN.eq(token))
                .execute();
        if (completed != 1 || (inserted != 1 && findFile(session.tenantId(), session.fileId()).isEmpty())) {
            throw new BusinessProblem(ProblemCode.FILE_UPLOAD_CONFLICT,
                    "Upload finalization lease was lost");
        }
        return findFile(session.tenantId(), session.fileId()).orElseThrow();
    }

    @Override
    public void failFinalization(String tenantId, UUID sessionId, UUID token, String errorCode, Instant now) {
        FilUploadSession table = FIL_UPLOAD_SESSION;
        dsl.update(table)
                .set(table.STATUS, "FAILED")
                .set(table.FAILURE_CODE, errorCode)
                .setNull(table.FINALIZATION_TOKEN)
                .setNull(table.FINALIZING_STARTED_AT)
                .set(table.UPDATED_AT, now)
                .where(table.TENANT_ID.eq(tenantId))
                .and(table.UPLOAD_SESSION_ID.eq(sessionId))
                .and(table.STATUS.eq("FINALIZING"))
                .and(table.FINALIZATION_TOKEN.eq(token))
                .execute();
    }

    @Override
    public void releaseFinalization(String tenantId, UUID sessionId, UUID token, String errorCode, Instant now) {
        FilUploadSession table = FIL_UPLOAD_SESSION;
        dsl.update(table)
                .set(table.STATUS, "CREATED")
                .set(table.FAILURE_CODE, errorCode)
                .setNull(table.FINALIZATION_TOKEN)
                .setNull(table.FINALIZING_STARTED_AT)
                .set(table.UPDATED_AT, now)
                .where(table.TENANT_ID.eq(tenantId))
                .and(table.UPLOAD_SESSION_ID.eq(sessionId))
                .and(table.STATUS.eq("FINALIZING"))
                .and(table.FINALIZATION_TOKEN.eq(token))
                .execute();
    }

    @Override
    public Optional<StoredFileRecord> findFile(String tenantId, UUID fileId) {
        FilStoredFile table = FIL_STORED_FILE;
        return dsl.select(
                        table.FILE_ID, table.TENANT_ID, table.UPLOAD_SESSION_ID, table.OBJECT_KEY,
                        table.ORIGINAL_FILE_NAME, table.CHECKSUM_SHA256, table.SIZE_BYTES,
                        table.DECLARED_MIME_TYPE, table.DETECTED_MIME_TYPE, table.LIFECYCLE_STATUS,
                        table.QUARANTINE_REASON, table.CREATED_AT, table.VERSION)
                .from(table)
                .where(table.TENANT_ID.eq(tenantId))
                .and(table.FILE_ID.eq(fileId))
                .fetchOptional(this::mapFile);
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

        FilScanResult scanResult = FIL_SCAN_RESULT;
        dsl.insertInto(scanResult)
                .set(scanResult.SCAN_RESULT_ID, UUID.randomUUID())
                .set(scanResult.TENANT_ID, tenantId)
                .set(scanResult.FILE_ID, fileId)
                .set(scanResult.TASK_ID, taskId)
                .set(scanResult.ATTEMPT_ID, attemptId)
                .set(scanResult.RESULT_CODE, outcome.result().name())
                .set(scanResult.SCANNER_NAME, outcome.scannerName())
                .set(scanResult.SCANNER_VERSION, outcome.scannerVersion())
                .set(scanResult.REASON_CODE, outcome.reasonCode())
                .set(scanResult.SCANNED_AT, now)
                .onConflict(scanResult.FILE_ID, scanResult.ATTEMPT_ID)
                .doNothing()
                .execute();

        String status = outcome.result() == ScanOutcome.Result.CLEAN ? "AVAILABLE" : "QUARANTINED";
        String reason = outcome.result() == ScanOutcome.Result.CLEAN ? null : "MALWARE";
        // 仅允许从 PENDING_SCAN 隔离态迁移；version+1 供下载授权等读侧感知内容变化。
        FilStoredFile file = FIL_STORED_FILE;
        dsl.update(file)
                .set(file.LIFECYCLE_STATUS, status)
                .set(file.QUARANTINE_REASON, reason)
                .set(file.UPDATED_AT, now)
                .set(file.VERSION, file.VERSION.plus(1))
                .where(file.TENANT_ID.eq(tenantId))
                .and(file.FILE_ID.eq(fileId))
                .and(file.LIFECYCLE_STATUS.eq("QUARANTINED"))
                .and(file.QUARANTINE_REASON.eq("PENDING_SCAN"))
                .execute();
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
        FilDownloadAuthorization table = FIL_DOWNLOAD_AUTHORIZATION;
        dsl.insertInto(table)
                .set(table.AUTHORIZATION_ID, authorizationId)
                .set(table.TENANT_ID, file.tenantId())
                .set(table.FILE_ID, file.fileId())
                .set(table.PRINCIPAL_ID, principalId)
                .set(table.PURPOSE, purpose)
                .set(table.CORRELATION_ID, correlationId)
                .set(table.ISSUED_AT, issuedAt)
                .set(table.EXPIRES_AT, expiresAt)
                .execute();
    }

    private Optional<UploadSessionRecord> sessionQuery(boolean forUpdate, String tenantId, UUID sessionId) {
        FilUploadSession table = FIL_UPLOAD_SESSION;
        var query = dsl.select(
                        table.UPLOAD_SESSION_ID, table.FILE_ID, table.TENANT_ID, table.OBJECT_KEY,
                        table.BUSINESS_CONTEXT_TYPE, table.BUSINESS_CONTEXT_ID, table.ORIGINAL_FILE_NAME,
                        table.DECLARED_MIME_TYPE, table.EXPECTED_SIZE, table.EXPECTED_SHA256, table.STATUS,
                        table.EXPIRES_AT, table.FINALIZE_REQUEST_DIGEST, table.FINALIZE_COMMAND_ID,
                        table.FINALIZATION_TOKEN, table.FINALIZING_STARTED_AT, table.CREATED_BY,
                        table.CREATED_AT)
                .from(table)
                .where(table.TENANT_ID.eq(tenantId))
                .and(table.UPLOAD_SESSION_ID.eq(sessionId));
        // finalize 租约判定必须锁住会话行，防止两个 finalize 并发通过状态检查。
        if (forUpdate) {
            return query.forUpdate().fetchOptional(this::mapSession);
        }
        return query.fetchOptional(this::mapSession);
    }

    private UploadSessionRecord mapSession(Record row) {
        FilUploadSession table = FIL_UPLOAD_SESSION;
        return new UploadSessionRecord(
                row.get(table.UPLOAD_SESSION_ID),
                row.get(table.FILE_ID),
                row.get(table.TENANT_ID),
                row.get(table.OBJECT_KEY),
                row.get(table.BUSINESS_CONTEXT_TYPE),
                row.get(table.BUSINESS_CONTEXT_ID),
                row.get(table.ORIGINAL_FILE_NAME),
                row.get(table.DECLARED_MIME_TYPE),
                row.get(table.EXPECTED_SIZE),
                row.get(table.EXPECTED_SHA256),
                row.get(table.STATUS),
                row.get(table.EXPIRES_AT),
                row.get(table.FINALIZE_REQUEST_DIGEST),
                row.get(table.FINALIZE_COMMAND_ID),
                row.get(table.FINALIZATION_TOKEN),
                row.get(table.FINALIZING_STARTED_AT),
                row.get(table.CREATED_BY),
                row.get(table.CREATED_AT));
    }

    @Override
    public int invalidateFile(String tenantId, UUID fileId, String expectedStatus, Instant now) {
        FilStoredFile table = FIL_STORED_FILE;
        return dsl.update(table)
                .set(table.LIFECYCLE_STATUS, "INVALIDATED")
                .set(table.UPDATED_AT, now)
                .set(table.VERSION, table.VERSION.plus(1))
                .where(table.TENANT_ID.eq(tenantId))
                .and(table.FILE_ID.eq(fileId))
                .and(table.LIFECYCLE_STATUS.eq(expectedStatus))
                .execute();
    }

    private StoredFileRecord mapFile(Record row) {
        FilStoredFile table = FIL_STORED_FILE;
        return new StoredFileRecord(
                row.get(table.FILE_ID),
                row.get(table.TENANT_ID),
                row.get(table.UPLOAD_SESSION_ID),
                row.get(table.OBJECT_KEY),
                row.get(table.ORIGINAL_FILE_NAME),
                row.get(table.CHECKSUM_SHA256),
                row.get(table.SIZE_BYTES),
                row.get(table.DECLARED_MIME_TYPE),
                row.get(table.DETECTED_MIME_TYPE),
                row.get(table.LIFECYCLE_STATUS),
                row.get(table.QUARANTINE_REASON),
                row.get(table.CREATED_AT),
                row.get(table.VERSION));
    }
}
