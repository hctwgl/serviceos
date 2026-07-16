-- M74：现场履约事件进入同一可重建工单时间线；仅 expand category 约束，不改既有列或索引。
ALTER TABLE rdm_work_order_timeline_entry
    DROP CONSTRAINT ck_rdm_work_order_timeline_category;

ALTER TABLE rdm_work_order_timeline_entry
    ADD CONSTRAINT ck_rdm_work_order_timeline_category CHECK (
        category IN (
            'WORK_ORDER',
            'WORKFLOW',
            'STAGE',
            'TASK',
            'APPOINTMENT',
            'VISIT',
            'CONTACT_ATTEMPT'
        )
    );
