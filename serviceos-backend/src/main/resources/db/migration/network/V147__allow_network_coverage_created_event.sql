-- 网点服务覆盖范围通过正式命令登记后，同事务记录目录事件，供审计和后续投影使用。
ALTER TABLE net_directory_event
    DROP CONSTRAINT ck_net_directory_event_type;

ALTER TABLE net_directory_event
    ADD CONSTRAINT ck_net_directory_event_type CHECK (
        event_type IN (
            'PARTNER_CREATED', 'NETWORK_CREATED', 'NETWORK_DEACTIVATED',
            'NETWORK_COVERAGE_CREATED',
            'MEMBERSHIP_INVITED', 'MEMBERSHIP_TERMINATED',
            'TECHNICIAN_CREATED', 'TECHNICIAN_DISABLED', 'TECHNICIAN_ENABLED',
            'TECHNICIAN_CLIENT_KINDS_DECLARED',
            'TECHNICIAN_MEMBERSHIP_CREATED', 'TECHNICIAN_MEMBERSHIP_TERMINATED',
            'QUALIFICATION_SUBMITTED', 'QUALIFICATION_DECIDED'
        )
    );

COMMENT ON CONSTRAINT ck_net_directory_event_type ON net_directory_event IS
'网点目录事件类型白名单。包含合作组织、网点、服务覆盖、成员、师傅和资质等受控变更。';
