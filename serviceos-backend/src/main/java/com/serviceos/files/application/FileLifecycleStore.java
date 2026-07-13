package com.serviceos.files.application;

import com.serviceos.files.spi.ObjectMetadata;
import com.serviceos.files.spi.ScanOutcome;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface FileLifecycleStore {
    void insertSession(UploadSessionRecord session);

    Optional<UploadSessionRecord> findSession(String tenantId, UUID sessionId);

    FinalizationReservation reserveFinalization(
            String tenantId,
            UUID sessionId,
            String actualSha256,
            String finalizeCommandId,
            String requestDigest,
            Instant now,
            Duration lease
    );

    StoredFileRecord completeFinalization(
            UploadSessionRecord session,
            UUID token,
            ObjectMetadata metadata,
            Instant now
    );

    void failFinalization(String tenantId, UUID sessionId, UUID token, String errorCode, Instant now);

    void releaseFinalization(String tenantId, UUID sessionId, UUID token, String errorCode, Instant now);

    Optional<StoredFileRecord> findFile(String tenantId, UUID fileId);

    StoredFileRecord recordScanResult(
            String tenantId,
            UUID fileId,
            UUID taskId,
            UUID attemptId,
            ScanOutcome outcome,
            Instant now
    );

    void insertDownloadAuthorization(
            UUID authorizationId,
            StoredFileRecord file,
            String principalId,
            String purpose,
            String correlationId,
            Instant issuedAt,
            Instant expiresAt
    );
}
