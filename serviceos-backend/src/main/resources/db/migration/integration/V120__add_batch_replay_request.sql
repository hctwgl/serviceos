-- M319：UNKNOWN OutboundDelivery 批量重放申请与审批。
-- 预演/提交生成不可变批次；批准后逐条复用单笔 :retry 语义，限流 max_items。

CREATE TABLE int_batch_replay_request (
    batch_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    mode varchar(24) NOT NULL,
    status varchar(32) NOT NULL,
    reason varchar(1000) NOT NULL,
    approval_ref varchar(160),
    requested_by varchar(160) NOT NULL,
    decided_by varchar(160),
    decision varchar(24),
    decision_note varchar(1000),
    max_items integer NOT NULL,
    created_at timestamptz NOT NULL,
    decided_at timestamptz,
    CONSTRAINT ck_int_batch_replay_mode CHECK (mode IN ('PREVIEW', 'SUBMIT')),
    CONSTRAINT ck_int_batch_replay_status CHECK (
        status IN ('PREVIEWED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'COMPLETED')),
    CONSTRAINT ck_int_batch_replay_max CHECK (max_items >= 1 AND max_items <= 50),
    CONSTRAINT ck_int_batch_replay_decision CHECK (
        (status IN ('PREVIEWED', 'PENDING_APPROVAL')
            AND decided_by IS NULL AND decision IS NULL AND decided_at IS NULL)
        OR
        (status IN ('APPROVED', 'REJECTED', 'COMPLETED')
            AND decided_by IS NOT NULL AND decision IS NOT NULL AND decided_at IS NOT NULL)
    ),
    CONSTRAINT ck_int_batch_replay_decision_value CHECK (
        decision IS NULL OR decision IN ('APPROVE', 'REJECT'))
);

CREATE TABLE int_batch_replay_item (
    batch_id uuid NOT NULL,
    delivery_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    project_id uuid,
    eligibility varchar(24) NOT NULL,
    ineligibility_code varchar(100),
    expected_delivery_version bigint,
    item_status varchar(24) NOT NULL,
    single_replay_request_id uuid,
    error_code varchar(100),
    PRIMARY KEY (batch_id, delivery_id),
    CONSTRAINT fk_int_batch_replay_item_batch
        FOREIGN KEY (batch_id) REFERENCES int_batch_replay_request(batch_id),
    CONSTRAINT fk_int_batch_replay_item_delivery
        FOREIGN KEY (delivery_id) REFERENCES int_outbound_delivery(delivery_id),
    CONSTRAINT ck_int_batch_replay_item_eligibility CHECK (
        eligibility IN ('ELIGIBLE', 'INELIGIBLE')),
    CONSTRAINT ck_int_batch_replay_item_status CHECK (
        item_status IN ('PREVIEWED', 'PENDING', 'SCHEDULED', 'SKIPPED', 'FAILED')),
    CONSTRAINT ck_int_batch_replay_item_eligible_shape CHECK (
        (eligibility = 'ELIGIBLE'
            AND expected_delivery_version IS NOT NULL AND project_id IS NOT NULL
            AND ineligibility_code IS NULL)
        OR
        (eligibility = 'INELIGIBLE'
            AND expected_delivery_version IS NULL AND single_replay_request_id IS NULL
            AND ineligibility_code IS NOT NULL)
    )
);

CREATE INDEX ix_int_batch_replay_request_tenant
    ON int_batch_replay_request (tenant_id, created_at DESC);
CREATE INDEX ix_int_batch_replay_item_delivery
    ON int_batch_replay_item (tenant_id, delivery_id);

CREATE OR REPLACE FUNCTION int_reject_batch_replay_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'batch replay request is immutable';
    END IF;
    IF TG_TABLE_NAME = 'int_batch_replay_item' THEN
        IF OLD.eligibility = 'INELIGIBLE' THEN
            RAISE EXCEPTION 'ineligible batch replay item is immutable';
        END IF;
        IF OLD.item_status IN ('SCHEDULED', 'SKIPPED', 'FAILED')
           AND ROW(NEW.*) IS DISTINCT FROM ROW(OLD.*) THEN
            RAISE EXCEPTION 'terminal batch replay item is immutable';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.status IN ('REJECTED', 'COMPLETED') THEN
        RAISE EXCEPTION 'terminal batch replay request is immutable';
    END IF;
    IF ROW(NEW.batch_id, NEW.tenant_id, NEW.mode, NEW.reason, NEW.approval_ref,
           NEW.requested_by, NEW.max_items, NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.batch_id, OLD.tenant_id, OLD.mode, OLD.reason, OLD.approval_ref,
           OLD.requested_by, OLD.max_items, OLD.created_at) THEN
        RAISE EXCEPTION 'batch replay request identity is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_int_batch_replay_request_immutable
    BEFORE UPDATE OR DELETE ON int_batch_replay_request
    FOR EACH ROW EXECUTE FUNCTION int_reject_batch_replay_mutation();

CREATE TRIGGER trg_int_batch_replay_item_immutable
    BEFORE UPDATE OR DELETE ON int_batch_replay_item
    FOR EACH ROW EXECUTE FUNCTION int_reject_batch_replay_mutation();

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('integration.batchReplayUnknownDelivery', '批量预演/审批 UNKNOWN 外部交付重放', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;

COMMENT ON TABLE int_batch_replay_request IS
    'M319：批量 UNKNOWN 重放申请；PREVIEW 仅审计，SUBMIT 待审批，批准后逐条调度单笔 replay';
COMMENT ON TABLE int_batch_replay_item IS
    'M319：批量重放条目资格与调度结果；ELIGIBLE 项批准后绑定 single replay request';
