-- 一个 Bundle 需要同时锁定勘测、安装等多个 FORM/EVIDENCE 资产版本。
-- 单例资产由 requireBundleAsset 在读取时失败关闭，而不是用物理主键错误限制所有类型。
ALTER TABLE cfg_configuration_bundle_item
    DROP CONSTRAINT cfg_configuration_bundle_item_pkey,
    DROP CONSTRAINT uq_cfg_bundle_asset_version,
    ADD PRIMARY KEY (bundle_id, asset_version_id);

CREATE INDEX ix_cfg_bundle_item_type
    ON cfg_configuration_bundle_item (bundle_id, asset_type, asset_version_id);
