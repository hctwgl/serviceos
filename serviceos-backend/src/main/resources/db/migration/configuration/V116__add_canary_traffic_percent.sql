-- M288：CANARY 通道百分比流量；STABLE 固定 100。

ALTER TABLE cfg_bundle_channel_activation
    ADD COLUMN traffic_percent integer NOT NULL DEFAULT 100;

ALTER TABLE cfg_bundle_channel_activation
    ADD CONSTRAINT ck_cfg_activation_traffic_percent CHECK (
        traffic_percent BETWEEN 0 AND 100
    );

ALTER TABLE cfg_bundle_channel_activation
    ADD CONSTRAINT ck_cfg_activation_stable_traffic CHECK (
        channel <> 'STABLE' OR traffic_percent = 100
    );

COMMENT ON COLUMN cfg_bundle_channel_activation.traffic_percent IS
    'CANARY 分流百分比；STABLE 必须为 100。hash(routingKey)%100 < traffic_percent 命中 CANARY。';
