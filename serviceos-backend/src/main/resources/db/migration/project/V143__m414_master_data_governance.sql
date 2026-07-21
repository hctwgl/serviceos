-- M414：车企品牌目录 + 国标省级骨架与演示地市/区县扩展。
-- 不宣称全国区县全量；拼音索引与多级子品牌树仍属后续切片。

CREATE TABLE prj_client_brand (
    tenant_id     varchar(64)  NOT NULL,
    client_code   varchar(128) NOT NULL,
    brand_code    varchar(128) NOT NULL,
    display_name  varchar(200) NOT NULL,
    brand_status  varchar(24)  NOT NULL,
    sort_order    integer      NOT NULL DEFAULT 0,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT pk_prj_client_brand PRIMARY KEY (tenant_id, client_code, brand_code),
    CONSTRAINT ck_prj_client_brand_code CHECK (brand_code = btrim(brand_code) AND brand_code <> ''),
    CONSTRAINT ck_prj_client_brand_name CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT ck_prj_client_brand_status CHECK (brand_status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT fk_prj_client_brand_client
        FOREIGN KEY (tenant_id, client_code)
        REFERENCES prj_client_directory (tenant_id, client_code)
);

CREATE INDEX ix_prj_client_brand_status
    ON prj_client_brand (tenant_id, client_code, brand_status, sort_order, brand_code);

-- 国标省级行政区骨架（6 位码）；已存在行保持不变。
INSERT INTO prj_region_catalog (region_code, parent_code, region_name, region_level, sort_order)
VALUES
    ('110000', NULL, '北京市', 'PROVINCE', 110000),
    ('120000', NULL, '天津市', 'PROVINCE', 120000),
    ('130000', NULL, '河北省', 'PROVINCE', 130000),
    ('140000', NULL, '山西省', 'PROVINCE', 140000),
    ('150000', NULL, '内蒙古自治区', 'PROVINCE', 150000),
    ('210000', NULL, '辽宁省', 'PROVINCE', 210000),
    ('220000', NULL, '吉林省', 'PROVINCE', 220000),
    ('230000', NULL, '黑龙江省', 'PROVINCE', 230000),
    ('310000', NULL, '上海市', 'PROVINCE', 310000),
    ('320000', NULL, '江苏省', 'PROVINCE', 320000),
    ('330000', NULL, '浙江省', 'PROVINCE', 330000),
    ('340000', NULL, '安徽省', 'PROVINCE', 340000),
    ('350000', NULL, '福建省', 'PROVINCE', 350000),
    ('360000', NULL, '江西省', 'PROVINCE', 360000),
    ('370000', NULL, '山东省', 'PROVINCE', 370000),
    ('410000', NULL, '河南省', 'PROVINCE', 410000),
    ('420000', NULL, '湖北省', 'PROVINCE', 420000),
    ('430000', NULL, '湖南省', 'PROVINCE', 430000),
    ('440000', NULL, '广东省', 'PROVINCE', 440000),
    ('450000', NULL, '广西壮族自治区', 'PROVINCE', 450000),
    ('460000', NULL, '海南省', 'PROVINCE', 460000),
    ('500000', NULL, '重庆市', 'PROVINCE', 500000),
    ('510000', NULL, '四川省', 'PROVINCE', 510000),
    ('520000', NULL, '贵州省', 'PROVINCE', 520000),
    ('530000', NULL, '云南省', 'PROVINCE', 530000),
    ('540000', NULL, '西藏自治区', 'PROVINCE', 540000),
    ('610000', NULL, '陕西省', 'PROVINCE', 610000),
    ('620000', NULL, '甘肃省', 'PROVINCE', 620000),
    ('630000', NULL, '青海省', 'PROVINCE', 630000),
    ('640000', NULL, '宁夏回族自治区', 'PROVINCE', 640000),
    ('650000', NULL, '新疆维吾尔自治区', 'PROVINCE', 650000),
    ('710000', NULL, '台湾省', 'PROVINCE', 710000),
    ('810000', NULL, '香港特别行政区', 'PROVINCE', 810000),
    ('820000', NULL, '澳门特别行政区', 'PROVINCE', 820000)
ON CONFLICT (region_code) DO NOTHING;

-- 演示地市 / 区县扩展（覆盖常用运营区域；非全国区县全量）。
INSERT INTO prj_region_catalog (region_code, parent_code, region_name, region_level, sort_order)
VALUES
    -- 北京（直辖市：省级下直接挂区）
    ('110101', '110000', '东城区', 'DISTRICT', 110101),
    ('110105', '110000', '朝阳区', 'DISTRICT', 110105),
    ('110108', '110000', '海淀区', 'DISTRICT', 110108),
    ('CN-1101', 'CN-1100', '东城区', 'DISTRICT', 1101),
    ('CN-1105', 'CN-1100', '朝阳区', 'DISTRICT', 1105),
    ('CN-1108', 'CN-1100', '海淀区', 'DISTRICT', 1108),
    -- 上海
    ('310101', '310000', '黄浦区', 'DISTRICT', 310101),
    ('310104', '310000', '徐汇区', 'DISTRICT', 310104),
    ('310115', '310000', '浦东新区', 'DISTRICT', 310115),
    ('CN-3101', 'CN-3100', '黄浦区', 'DISTRICT', 3101),
    ('CN-3115', 'CN-3100', '浦东新区', 'DISTRICT', 3115),
    -- 广东
    ('440100', '440000', '广州市', 'CITY', 440100),
    ('440103', '440100', '荔湾区', 'DISTRICT', 440103),
    ('440106', '440100', '天河区', 'DISTRICT', 440106),
    ('440300', '440000', '深圳市', 'CITY', 440300),
    ('440303', '440300', '罗湖区', 'DISTRICT', 440303),
    ('440304', '440300', '福田区', 'DISTRICT', 440304),
    ('440305', '440300', '南山区', 'DISTRICT', 440305),
    ('440400', '440000', '珠海市', 'CITY', 440400),
    ('CN-4401', 'CN-4400', '广州市', 'CITY', 4401),
    ('CN-4403', 'CN-4400', '深圳市', 'CITY', 4403),
    ('CN-440303', 'CN-4403', '罗湖区', 'DISTRICT', 440303),
    ('CN-440304', 'CN-4403', '福田区', 'DISTRICT', 440304),
    ('CN-440305', 'CN-4403', '南山区', 'DISTRICT', 440305),
    -- 山东
    ('370100', '370000', '济南市', 'CITY', 370100),
    ('370102', '370100', '历下区', 'DISTRICT', 370102),
    ('370200', '370000', '青岛市', 'CITY', 370200),
    ('370202', '370200', '市南区', 'DISTRICT', 370202),
    ('370203', '370200', '市北区', 'DISTRICT', 370203),
    ('370211', '370200', '黄岛区', 'DISTRICT', 370211),
    ('CN-3701', 'CN-3700', '济南市', 'CITY', 3701),
    ('CN-3702', 'CN-3700', '青岛市', 'CITY', 3702),
    ('CN-370202', 'CN-3702', '市南区', 'DISTRICT', 370202),
    ('CN-370211', 'CN-3702', '黄岛区', 'DISTRICT', 370211),
    -- 四川
    ('510100', '510000', '成都市', 'CITY', 510100),
    ('510104', '510100', '锦江区', 'DISTRICT', 510104),
    ('510107', '510100', '武侯区', 'DISTRICT', 510107),
    ('CN-5101', 'CN-5100', '成都市', 'CITY', 5101),
    ('CN-510104', 'CN-5101', '锦江区', 'DISTRICT', 510104),
    -- 江苏 / 浙江（省级已有，补主要城市）
    ('320100', '320000', '南京市', 'CITY', 320100),
    ('320500', '320000', '苏州市', 'CITY', 320500),
    ('330100', '330000', '杭州市', 'CITY', 330100),
    ('330200', '330000', '宁波市', 'CITY', 330200),
    -- 天津 / 重庆部分区
    ('120101', '120000', '和平区', 'DISTRICT', 120101),
    ('120116', '120000', '滨海新区', 'DISTRICT', 120116),
    ('500103', '500000', '渝中区', 'DISTRICT', 500103),
    ('500107', '500000', '九龙坡区', 'DISTRICT', 500107)
ON CONFLICT (region_code) DO NOTHING;
