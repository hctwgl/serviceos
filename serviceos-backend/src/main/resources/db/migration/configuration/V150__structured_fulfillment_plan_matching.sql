-- DEC-007 / AD-014：允许同一服务产品配置多套履约方案，并以稳定方案编码和优先级匹配。
-- 适用范围快照仍随 Revision 的 document/manifest 原子冻结；本迁移只扩展方案身份和排序事实。

ALTER TABLE cfg_project_fulfillment_profile
    ADD COLUMN profile_code varchar(96),
    ADD COLUMN match_priority integer;

UPDATE cfg_project_fulfillment_profile
   SET profile_code = service_product_code,
       match_priority = 0
 WHERE profile_code IS NULL
    OR match_priority IS NULL;

ALTER TABLE cfg_project_fulfillment_profile
    ALTER COLUMN profile_code SET NOT NULL,
    ALTER COLUMN match_priority SET NOT NULL,
    DROP CONSTRAINT uq_cfg_pfp_project_product,
    ADD CONSTRAINT uq_cfg_pfp_project_code
        UNIQUE (tenant_id, project_id, profile_code),
    ADD CONSTRAINT ck_cfg_pfp_match_priority
        CHECK (match_priority BETWEEN -10000 AND 10000);

CREATE INDEX ix_cfg_pfp_match_candidates
    ON cfg_project_fulfillment_profile (
        tenant_id,
        project_id,
        service_product_code,
        status,
        match_priority DESC
    );

COMMENT ON COLUMN cfg_project_fulfillment_profile.profile_code IS
    '项目内稳定履约方案编码，不等同于服务产品编码';
COMMENT ON COLUMN cfg_project_fulfillment_profile.match_priority IS
    '履约方案匹配优先级，越大越优先；同优先级再按规则具体度';
