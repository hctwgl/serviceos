CREATE TABLE int_inbound_replay_guard (
    app_key varchar(128) NOT NULL,
    nonce varchar(128) NOT NULL,
    current_time bigint NOT NULL,
    payload_digest char(64) NOT NULL,
    first_seen_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    result_digest char(64),
    PRIMARY KEY (app_key, nonce, current_time)
);

CREATE INDEX idx_int_inbound_replay_guard_expires_at
    ON int_inbound_replay_guard (expires_at);
