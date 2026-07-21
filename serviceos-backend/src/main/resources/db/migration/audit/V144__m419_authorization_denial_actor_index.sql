-- M419：按被拒主体（actor）检索 AUTHORIZATION_DENIED，支撑独立安全活动流。
CREATE INDEX ix_aud_audit_actor_authorization_denied
    ON aud_audit_record (tenant_id, actor_id, occurred_at DESC, audit_id DESC)
    WHERE action_name = 'AUTHORIZATION_DENIED';
