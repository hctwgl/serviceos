-- M358：FORM/EVIDENCE 定向发布目标客户端。
-- 草稿列 / 已发布侧表缺省（null/无行）= 全部生产师傅端（保持 M356 默认语义）；
-- 非空数组 = 声明子集，并对子集内每一端做硬兼容校验。

ALTER TABLE cfg_configuration_asset_draft
    ADD COLUMN supported_client_kinds jsonb;

ALTER TABLE cfg_configuration_asset_draft
    ADD CONSTRAINT ck_cfg_draft_supported_client_kinds CHECK (
        supported_client_kinds IS NULL
        OR (
            jsonb_typeof(supported_client_kinds) = 'array'
            AND jsonb_array_length(supported_client_kinds) >= 1
            AND supported_client_kinds <@ '["TECHNICIAN_WEB", "TECHNICIAN_IOS"]'::jsonb
        )
    );

CREATE TABLE cfg_configuration_asset_client_target (
    version_id              uuid         NOT NULL,
    tenant_id               varchar(64)  NOT NULL,
    supported_client_kinds  jsonb        NOT NULL,
    CONSTRAINT pk_cfg_asset_client_target PRIMARY KEY (version_id),
    CONSTRAINT fk_cfg_asset_client_target_version
        FOREIGN KEY (version_id) REFERENCES cfg_configuration_asset_version (version_id),
    CONSTRAINT ck_cfg_asset_client_target_kinds CHECK (
        jsonb_typeof(supported_client_kinds) = 'array'
        AND jsonb_array_length(supported_client_kinds) >= 1
        AND supported_client_kinds <@ '["TECHNICIAN_WEB", "TECHNICIAN_IOS"]'::jsonb
    )
);

CREATE INDEX ix_cfg_asset_client_target_tenant
    ON cfg_configuration_asset_client_target (tenant_id, version_id);

-- 已发布资产目标客户端随版本不可变：禁止 UPDATE/DELETE。
CREATE OR REPLACE FUNCTION cfg_reject_asset_client_target_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'cfg_configuration_asset_client_target is immutable';
END;
$$;

CREATE TRIGGER trg_cfg_asset_client_target_immutable
    BEFORE UPDATE OR DELETE ON cfg_configuration_asset_client_target
    FOR EACH ROW EXECUTE FUNCTION cfg_reject_asset_client_target_mutation();
