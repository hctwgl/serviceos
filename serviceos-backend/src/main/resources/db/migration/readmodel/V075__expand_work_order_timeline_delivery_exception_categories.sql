-- M77：外发交付创建与运营异常闭环进入同一可重建工单时间线；仅 expand category 约束。
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
            'CONTACT_ATTEMPT',
            'SLA',
            'FORM',
            'EVIDENCE',
            'REVIEW',
            'CORRECTION',
            'DELIVERY',
            'EXCEPTION'
        )
    );
