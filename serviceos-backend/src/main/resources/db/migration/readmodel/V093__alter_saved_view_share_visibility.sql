-- M191：Admin 共享 SavedView。visibility ROLE/TENANT 只共享查询定义，不授予数据能力。
-- TENANT 表达租户级共享（逻辑稿 ORGANIZATION 组织树共享仍未接受）。

ALTER TABLE rdm_saved_view
    ADD COLUMN visibility varchar(32) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN shared_scope_ref varchar(128);

ALTER TABLE rdm_saved_view
    ADD CONSTRAINT ck_rdm_saved_view_visibility
        CHECK (visibility IN ('PRIVATE', 'ROLE', 'TENANT')),
    ADD CONSTRAINT ck_rdm_saved_view_shared_scope
        CHECK (
            (visibility = 'ROLE' AND shared_scope_ref IS NOT NULL AND length(btrim(shared_scope_ref)) > 0)
            OR (visibility IN ('PRIVATE', 'TENANT') AND shared_scope_ref IS NULL)
        );

-- 列表：同租户页面下按可见性合并本人 PRIVATE 与可见共享视图。
CREATE INDEX ix_rdm_saved_view_tenant_page_visibility
    ON rdm_saved_view (tenant_id, portal, page_id, visibility, updated_at DESC);

CREATE INDEX ix_rdm_saved_view_tenant_page_role
    ON rdm_saved_view (tenant_id, portal, page_id, shared_scope_ref)
    WHERE visibility = 'ROLE';

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('preference.shareSavedView', '共享 Admin SavedView 查询定义', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;
