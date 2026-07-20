-- M365：REVIEW_TASK 门闸早期通过信号。
-- 审核决定可能早于 INSTALL 完成到达门闸；先落 durable token，门闸激活时再消费。

CREATE TABLE wfl_review_gate_early_signal (
    tenant_id           varchar(64)  NOT NULL,
    work_order_id       uuid         NOT NULL,
    review_case_id      uuid         NOT NULL,
    review_decision_id  uuid         NOT NULL,
    decision            varchar(32)  NOT NULL,
    signal_id           varchar(128) NOT NULL,
    correlation_id      varchar(128) NOT NULL,
    created_at          timestamptz  NOT NULL,
    consumed_at         timestamptz,
    CONSTRAINT pk_wfl_review_gate_early_signal PRIMARY KEY (tenant_id, work_order_id),
    CONSTRAINT ck_wfl_review_gate_early_decision
        CHECK (decision IN ('APPROVED', 'FORCE_APPROVED'))
);

CREATE UNIQUE INDEX uq_wfl_review_gate_early_signal_id
    ON wfl_review_gate_early_signal (tenant_id, signal_id);

COMMENT ON TABLE wfl_review_gate_early_signal IS
    'M365：APPROVED/FORCE_APPROVED 早于 REVIEW_TASK 门闸激活时的 durable token；每工单一条，后写覆盖';
