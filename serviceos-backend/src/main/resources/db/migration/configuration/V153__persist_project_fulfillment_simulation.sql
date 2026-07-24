-- Workflow Designer 的模拟结果必须绑定到草稿内容摘要。
-- 任何草稿修改都会清空模拟证据，发布只能使用当前文档对应的成功模拟。

ALTER TABLE cfg_project_fulfillment_revision
    ADD COLUMN simulation_json jsonb,
    ADD COLUMN simulation_document_digest char(64),
    ADD COLUMN simulated_at timestamptz;

ALTER TABLE cfg_project_fulfillment_revision
    ADD CONSTRAINT ck_cfg_pfr_simulation_digest CHECK (
        simulation_document_digest IS NULL
        OR simulation_document_digest ~ '^[0-9a-f]{64}$'
    ),
    ADD CONSTRAINT ck_cfg_pfr_simulation_shape CHECK (
        (simulation_json IS NULL
            AND simulation_document_digest IS NULL
            AND simulated_at IS NULL)
        OR (simulation_json IS NOT NULL
            AND simulation_document_digest IS NOT NULL
            AND simulated_at IS NOT NULL)
    );
