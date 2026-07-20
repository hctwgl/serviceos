-- M406：车企主数据目录（租户级）与行政区名称目录（全局只读字典）。
-- 区域编码兼容历史 CN-xxxx 与国标 6 位码；不替代项目 REGION 授权事实。

CREATE TABLE prj_client_directory (
    tenant_id     varchar(64)  NOT NULL,
    client_code   varchar(128) NOT NULL,
    display_name  varchar(200) NOT NULL,
    client_status varchar(24)  NOT NULL,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT pk_prj_client_directory PRIMARY KEY (tenant_id, client_code),
    CONSTRAINT ck_prj_client_directory_code CHECK (client_code = btrim(client_code) AND client_code <> ''),
    CONSTRAINT ck_prj_client_directory_name CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT ck_prj_client_directory_status CHECK (client_status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX ix_prj_client_directory_status
    ON prj_client_directory (tenant_id, client_status, client_code);

CREATE TABLE prj_region_catalog (
    region_code   varchar(32)  NOT NULL,
    parent_code   varchar(32),
    region_name   varchar(100) NOT NULL,
    region_level  varchar(16)  NOT NULL,
    sort_order    integer      NOT NULL DEFAULT 0,
    region_status varchar(24)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_prj_region_catalog PRIMARY KEY (region_code),
    CONSTRAINT ck_prj_region_catalog_code CHECK (region_code = btrim(region_code) AND region_code <> ''),
    CONSTRAINT ck_prj_region_catalog_name CHECK (length(btrim(region_name)) > 0),
    CONSTRAINT ck_prj_region_catalog_level CHECK (region_level IN ('PROVINCE', 'CITY', 'DISTRICT')),
    CONSTRAINT ck_prj_region_catalog_status CHECK (region_status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT fk_prj_region_catalog_parent
        FOREIGN KEY (parent_code) REFERENCES prj_region_catalog (region_code)
);

CREATE INDEX ix_prj_region_catalog_parent
    ON prj_region_catalog (parent_code, sort_order, region_code);

CREATE INDEX ix_prj_region_catalog_name
    ON prj_region_catalog (region_name);

-- 覆盖测试与演示常用编码；完整国标树可后续增量迁移扩展。
INSERT INTO prj_region_catalog (region_code, parent_code, region_name, region_level, sort_order) VALUES
    ('CN-1100', NULL, '北京市', 'PROVINCE', 1100),
    ('CN-3100', NULL, '上海市', 'PROVINCE', 3100),
    ('CN-4400', NULL, '广东省', 'PROVINCE', 4400),
    ('CN-4403', 'CN-4400', '深圳市', 'CITY', 4403),
    ('CN-5100', NULL, '四川省', 'PROVINCE', 5100),
    ('CN-3700', NULL, '山东省', 'PROVINCE', 3700),
    ('CN-3702', 'CN-3700', '青岛市', 'CITY', 3702),
    ('370000', NULL, '山东省', 'PROVINCE', 370000),
    ('370100', '370000', '济南市', 'CITY', 370100),
    ('370200', '370000', '青岛市', 'CITY', 370200),
    ('440000', NULL, '广东省', 'PROVINCE', 440000),
    ('440300', '440000', '深圳市', 'CITY', 440300),
    ('110000', NULL, '北京市', 'PROVINCE', 110000),
    ('310000', NULL, '上海市', 'PROVINCE', 310000);
