-- M43: 冻结表单+资料双引用完成条件的结构化输入版本。

ALTER TABLE tsk_task
    ADD COLUMN input_version_refs jsonb;

ALTER TABLE tsk_task
    ADD CONSTRAINT ck_tsk_input_version_refs CHECK (
        input_version_refs IS NULL OR jsonb_typeof(input_version_refs) = 'array'
    );

COMMENT ON COLUMN tsk_task.input_version_refs IS
    'M43 完成时冻结的结构化输入版本引用数组；双引用 Task 必填，历史 Snapshot/Submission 不可改写';
