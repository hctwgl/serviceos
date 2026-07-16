-- M86：登记 work-order-core-timeline.v1 的 projection_definition。
-- 仅权威元数据；不引入多投影平台或 Admin HTTP。

CREATE TABLE rdm_projection_definition (
    projection_code         varchar(120) NOT NULL,
    schema_version          integer      NOT NULL,
    source_event_types      jsonb        NOT NULL,
    partition_strategy      varchar(64)  NOT NULL,
    rebuild_policy          varchar(64)  NOT NULL,
    freshness_target        varchar(64)  NOT NULL,
    owner_module            varchar(64)  NOT NULL,
    created_at              timestamptz  NOT NULL,
    updated_at              timestamptz  NOT NULL,
    CONSTRAINT pk_rdm_projection_definition PRIMARY KEY (projection_code),
    CONSTRAINT ck_rdm_projection_definition_schema CHECK (schema_version > 0),
    CONSTRAINT ck_rdm_projection_definition_partition CHECK (
        partition_strategy IN ('TENANT_SINGLE')
    ),
    CONSTRAINT ck_rdm_projection_definition_rebuild CHECK (
        rebuild_policy IN ('FULL_SCAN_PUBLISHED_OUTBOX')
    ),
    CONSTRAINT ck_rdm_projection_definition_freshness CHECK (
        freshness_target IN ('CHECKPOINT_AND_DEAD_LETTER')
    )
);

INSERT INTO rdm_projection_definition (
    projection_code, schema_version, source_event_types, partition_strategy,
    rebuild_policy, freshness_target, owner_module, created_at, updated_at
) VALUES (
    'work-order-core-timeline.v1',
    1,
    '[
      "workorder.received","workorder.activated","workorder.fulfilled",
      "workflow.started","workflow.completed",
      "stage.activated","stage.completed",
      "task.created","task.claimed","task.started","task.released",
      "task.cancelled","task.completed","task.assigned",
      "task.assignment-prepared","task.assignment-activated","task.assignment-aborted",
      "task.execution-guard.activated","task.execution-guard.released",
      "task.execution.manual-intervention-required",
      "contact.attempt.recorded",
      "appointment.proposed","appointment.confirmed","appointment.rescheduled",
      "appointment.cancelled","appointment.no-show-marked",
      "visit.checked-in","visit.checked-out","visit.interrupted",
      "sla.started","sla.breached","sla.met",
      "form.submitted","evidence.set-snapshotted",
      "evidence.review-case-created","evidence.client-review-case-created",
      "evidence.review-decided","evidence.review-case-reopened",
      "evidence.correction-case-created","evidence.correction-resubmitted",
      "evidence.correction-closed","evidence.correction-waived",
      "evidence.condition-disposition-recorded",
      "evidence.external-review-receipt-recorded",
      "integration.outbound-delivery-created",
      "integration.outbound-delivery-acknowledged",
      "integration.outbound-delivery-recovered",
      "integration.outbound-delivery-replay-requested",
      "operational.exception.acknowledged","operational.exception.resolved",
      "service.assignment.pending-activation","service.assignment.task-prepared",
      "service.assignment.activated","service.assignment.activation-aborted",
      "service.assignment.activation-completed",
      "service.assignment.activation-abort-completed",
      "service.assignment.activation-timed-out"
    ]'::jsonb,
    'TENANT_SINGLE',
    'FULL_SCAN_PUBLISHED_OUTBOX',
    'CHECKPOINT_AND_DEAD_LETTER',
    'readmodel',
    now(),
    now()
);
