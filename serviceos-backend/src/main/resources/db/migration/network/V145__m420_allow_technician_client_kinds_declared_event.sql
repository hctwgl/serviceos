-- M420：允许师傅客户端种类声明事件落库（此前 complete 写入但 CHECK 未收录）。
ALTER TABLE net_directory_event
    DROP CONSTRAINT ck_net_directory_event_type;

ALTER TABLE net_directory_event
    ADD CONSTRAINT ck_net_directory_event_type CHECK (
        event_type IN (
            'PARTNER_CREATED', 'NETWORK_CREATED', 'NETWORK_DEACTIVATED',
            'MEMBERSHIP_INVITED', 'MEMBERSHIP_TERMINATED',
            'TECHNICIAN_CREATED', 'TECHNICIAN_DISABLED', 'TECHNICIAN_ENABLED',
            'TECHNICIAN_CLIENT_KINDS_DECLARED',
            'TECHNICIAN_MEMBERSHIP_CREATED', 'TECHNICIAN_MEMBERSHIP_TERMINATED',
            'QUALIFICATION_SUBMITTED', 'QUALIFICATION_DECIDED'
        )
    );
