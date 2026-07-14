ALTER TABLE dsp_service_assignment
    ADD COLUMN activation_protocol_version integer NOT NULL DEFAULT 1,
    ADD COLUMN pending_authority_assignment_id varchar(160),
    ADD COLUMN pending_authority_version bigint,
    ADD COLUMN pending_fence_decision_id varchar(160),
    ADD COLUMN pending_fence_policy_version varchar(160),
    ADD CONSTRAINT ck_dsp_assignment_protocol CHECK (
        activation_protocol_version IN (1, 2)
        AND (
            activation_protocol_version = 1
            OR (
                supersedes_service_assignment_id IS NOT NULL
                AND pending_authority_assignment_id IS NOT NULL
                AND pending_authority_version IS NOT NULL
                AND pending_authority_version > 0
                AND pending_fence_decision_id IS NOT NULL
                AND pending_fence_policy_version IS NOT NULL
            )
        )
    );
