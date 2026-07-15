ALTER TABLE evd_task_evidence_resolution
    ADD COLUMN condition_input_digest char(64) NOT NULL
        DEFAULT '44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a',
    ADD COLUMN resolution_explanation jsonb NOT NULL
        DEFAULT '{"kind":"BASELINE_BACKFILL","resolverVersion":"FIXED_EVIDENCE_V1"}'::jsonb;

-- 默认值仅用于把 M37-M51 已存在的不可变解析事实升级到新结构；M52 运行时必须显式写入。
ALTER TABLE evd_task_evidence_resolution
    ALTER COLUMN condition_input_digest DROP DEFAULT,
    ALTER COLUMN resolution_explanation DROP DEFAULT,
    ADD CONSTRAINT ck_evd_resolution_condition_digest CHECK (
        condition_input_digest ~ '^[0-9a-f]{64}$'
    ),
    ADD CONSTRAINT ck_evd_resolution_explanation_object CHECK (
        jsonb_typeof(resolution_explanation) = 'object'
    );
