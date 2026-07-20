-- M366 / ADR-088 A2-R：师傅履约端声明（派单硬过滤权威来源）。
-- null = 未声明（仅兼容资产未定向任务）；非空 = TECHNICIAN_WEB / TECHNICIAN_IOS 子集。

ALTER TABLE net_technician_profile
    ADD COLUMN supported_client_kinds jsonb;

ALTER TABLE net_technician_profile
    ADD CONSTRAINT ck_net_technician_supported_client_kinds CHECK (
        supported_client_kinds IS NULL
        OR (
            jsonb_typeof(supported_client_kinds) = 'array'
            AND jsonb_array_length(supported_client_kinds) >= 1
            AND supported_client_kinds <@ '["TECHNICIAN_WEB", "TECHNICIAN_IOS"]'::jsonb
        )
    );
