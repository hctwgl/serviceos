CREATE TABLE rel_inbox_record (
    tenant_id       varchar(64)  NOT NULL,
    consumer_name   varchar(128) NOT NULL,
    event_id        uuid         NOT NULL,
    schema_version  integer      NOT NULL,
    payload_digest  char(64)     NOT NULL,
    status          varchar(24)  NOT NULL,
    result_digest   char(64),
    started_at      timestamptz  NOT NULL,
    completed_at    timestamptz,
    CONSTRAINT pk_rel_inbox_record PRIMARY KEY (tenant_id, consumer_name, event_id),
    CONSTRAINT ck_rel_inbox_status CHECK (status IN ('PROCESSING', 'SUCCEEDED'))
);

CREATE INDEX ix_rel_inbox_started
    ON rel_inbox_record (tenant_id, consumer_name, started_at);

CREATE TABLE rel_outbox_publish_attempt (
    attempt_id    uuid         NOT NULL,
    outbox_id     uuid         NOT NULL,
    attempt_no    integer      NOT NULL,
    worker_id     varchar(128) NOT NULL,
    started_at    timestamptz  NOT NULL,
    finished_at   timestamptz  NOT NULL,
    result_code   varchar(24)  NOT NULL,
    error_code    varchar(100),
    CONSTRAINT pk_rel_outbox_publish_attempt PRIMARY KEY (attempt_id),
    CONSTRAINT fk_rel_outbox_attempt_event
        FOREIGN KEY (outbox_id) REFERENCES rel_outbox_event (outbox_id),
    CONSTRAINT uq_rel_outbox_attempt_number UNIQUE (outbox_id, attempt_no),
    CONSTRAINT ck_rel_outbox_attempt_no CHECK (attempt_no > 0),
    CONSTRAINT ck_rel_outbox_attempt_result
        CHECK (result_code IN ('PUBLISHED', 'FAILED', 'DEAD'))
);

CREATE INDEX ix_rel_outbox_attempt_event
    ON rel_outbox_publish_attempt (outbox_id, attempt_no DESC);
