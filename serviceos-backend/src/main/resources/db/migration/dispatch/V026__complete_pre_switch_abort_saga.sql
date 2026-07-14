-- M26 将 Dispatch 本地终止与 Task PREPARED 撤销拆成 ABORTING -> ABORTED 两段可靠握手。
-- 历史 M24/M25 的 ABORTED 行没有完成时间，先以最后更新时间回填后再收紧终态约束。
UPDATE dsp_service_assignment_activation_saga
   SET completed_at = updated_at
 WHERE stage = 'ABORTED' AND completed_at IS NULL;

ALTER TABLE dsp_service_assignment_activation_saga
    DROP CONSTRAINT ck_dsp_saga_completion,
    ADD CONSTRAINT ck_dsp_saga_completion CHECK (
        (stage IN ('COMPLETED', 'ABORTED') AND completed_at IS NOT NULL)
        OR (stage NOT IN ('COMPLETED', 'ABORTED') AND completed_at IS NULL)
    );
