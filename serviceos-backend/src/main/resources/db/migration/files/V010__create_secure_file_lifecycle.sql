CREATE TABLE fil_upload_session (
    upload_session_id       uuid          NOT NULL,
    file_id                 uuid          NOT NULL,
    tenant_id               varchar(64)   NOT NULL,
    object_key              varchar(500)  NOT NULL,
    business_context_type   varchar(80)   NOT NULL,
    business_context_id     varchar(160)  NOT NULL,
    original_file_name      varchar(255)  NOT NULL,
    declared_mime_type      varchar(120)  NOT NULL,
    expected_size           bigint        NOT NULL,
    expected_sha256         char(64)      NOT NULL,
    status                  varchar(24)   NOT NULL,
    expires_at              timestamptz   NOT NULL,
    finalize_request_digest char(64),
    finalize_command_id     varchar(160),
    finalization_token      uuid,
    finalizing_started_at   timestamptz,
    failure_code            varchar(100),
    created_by              varchar(128)  NOT NULL,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    completed_at            timestamptz,
    CONSTRAINT pk_fil_upload_session PRIMARY KEY (upload_session_id),
    CONSTRAINT uq_fil_upload_file UNIQUE (file_id),
    CONSTRAINT uq_fil_upload_object UNIQUE (object_key),
    CONSTRAINT ck_fil_upload_status CHECK (
        status IN ('CREATED', 'UPLOADING', 'FINALIZING', 'COMPLETED', 'EXPIRED', 'FAILED')
    ),
    CONSTRAINT ck_fil_upload_size CHECK (expected_size > 0),
    CONSTRAINT ck_fil_upload_finalize CHECK (
        (status = 'FINALIZING'
            AND finalize_request_digest IS NOT NULL
            AND finalize_command_id IS NOT NULL
            AND finalization_token IS NOT NULL
            AND finalizing_started_at IS NOT NULL)
        OR
        (status <> 'FINALIZING' AND finalization_token IS NULL AND finalizing_started_at IS NULL)
    )
);

CREATE INDEX ix_fil_upload_expiry
    ON fil_upload_session (expires_at, upload_session_id)
    WHERE status IN ('CREATED', 'UPLOADING', 'FINALIZING');

CREATE TABLE fil_stored_file (
    file_id                uuid          NOT NULL,
    tenant_id              varchar(64)   NOT NULL,
    upload_session_id      uuid          NOT NULL,
    object_key             varchar(500)  NOT NULL,
    original_file_name     varchar(255)  NOT NULL,
    checksum_sha256        char(64)      NOT NULL,
    size_bytes             bigint        NOT NULL,
    declared_mime_type     varchar(120)  NOT NULL,
    detected_mime_type     varchar(120)  NOT NULL,
    lifecycle_status       varchar(24)   NOT NULL,
    quarantine_reason      varchar(100),
    created_by             varchar(128)  NOT NULL,
    created_at             timestamptz   NOT NULL,
    updated_at             timestamptz   NOT NULL,
    version                bigint        NOT NULL DEFAULT 1,
    CONSTRAINT pk_fil_stored_file PRIMARY KEY (file_id),
    CONSTRAINT fk_fil_file_session FOREIGN KEY (upload_session_id)
        REFERENCES fil_upload_session (upload_session_id),
    CONSTRAINT uq_fil_file_session UNIQUE (upload_session_id),
    CONSTRAINT uq_fil_file_object UNIQUE (object_key),
    CONSTRAINT ck_fil_file_size CHECK (size_bytes > 0),
    CONSTRAINT ck_fil_file_status CHECK (
        lifecycle_status IN ('QUARANTINED', 'AVAILABLE', 'INVALIDATED')
    ),
    CONSTRAINT ck_fil_file_quarantine CHECK (
        (lifecycle_status = 'QUARANTINED' AND quarantine_reason IS NOT NULL)
        OR (lifecycle_status <> 'QUARANTINED' AND quarantine_reason IS NULL)
    )
);

CREATE INDEX ix_fil_file_tenant_status
    ON fil_stored_file (tenant_id, lifecycle_status, created_at DESC);

CREATE TABLE fil_scan_result (
    scan_result_id   uuid          NOT NULL,
    tenant_id        varchar(64)   NOT NULL,
    file_id          uuid          NOT NULL,
    task_id          uuid          NOT NULL,
    attempt_id       uuid          NOT NULL,
    result_code      varchar(24)   NOT NULL,
    scanner_name     varchar(120)  NOT NULL,
    scanner_version  varchar(80)   NOT NULL,
    reason_code      varchar(100),
    scanned_at       timestamptz   NOT NULL,
    CONSTRAINT pk_fil_scan_result PRIMARY KEY (scan_result_id),
    CONSTRAINT fk_fil_scan_file FOREIGN KEY (file_id) REFERENCES fil_stored_file (file_id),
    CONSTRAINT uq_fil_scan_attempt UNIQUE (file_id, attempt_id),
    CONSTRAINT ck_fil_scan_result CHECK (result_code IN ('CLEAN', 'MALICIOUS')),
    CONSTRAINT ck_fil_scan_reason CHECK (
        (result_code = 'MALICIOUS' AND reason_code IS NOT NULL)
        OR (result_code = 'CLEAN' AND reason_code IS NULL)
    )
);

CREATE TABLE fil_download_authorization (
    authorization_id  uuid          NOT NULL,
    tenant_id         varchar(64)   NOT NULL,
    file_id           uuid          NOT NULL,
    principal_id      varchar(128)  NOT NULL,
    purpose           varchar(500)  NOT NULL,
    correlation_id    varchar(128)  NOT NULL,
    issued_at         timestamptz   NOT NULL,
    expires_at        timestamptz   NOT NULL,
    CONSTRAINT pk_fil_download_authorization PRIMARY KEY (authorization_id),
    CONSTRAINT fk_fil_download_file FOREIGN KEY (file_id) REFERENCES fil_stored_file (file_id),
    CONSTRAINT ck_fil_download_period CHECK (expires_at > issued_at)
);

CREATE INDEX ix_fil_download_file_audit
    ON fil_download_authorization (tenant_id, file_id, issued_at DESC);

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('file.upload', '创建并完成文件上传', 'HIGH', now()),
    ('file.download', '申请文件短期下载授权', 'HIGH', now());
