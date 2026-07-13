ALTER TABLE aud_audit_record
    ADD COLUMN capability_code varchar(120),
    ADD COLUMN decision_code varchar(24),
    ADD COLUMN matched_grant_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN authorization_policy_version varchar(120),
    ADD COLUMN error_code varchar(100);

CREATE INDEX ix_aud_audit_denial
    ON aud_audit_record (tenant_id, decision_code, occurred_at DESC)
    WHERE decision_code = 'DENY';
