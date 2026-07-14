---
title: M43 表单+资料双引用完成门禁
version: 0.1.0
status: Implemented
---

# M43 表单+资料双引用完成门禁

## 1. 实现边界

关闭 M41 留下的正确性缺口：同时具备 `formRef` 与非空 EvidenceSlot 的 HUMAN Task，
不得再只校验表单而跳过资料。

规则：

1. **表单-only**：`formRef` 非空且无非空 EvidenceSlot → 仍按 M35，仅 `resultRef=form-submission://…`；
2. **资料-only**：无 `formRef` 且有非空 EvidenceSlot → 仍按 M41，仅 `resultRef=evidence-set-snapshot://…`；
3. **双引用**：`formRef` 非空且已解析出非空 EvidenceSlot →
   - `resultRef` / `resultDigest` 仍指向权威 FormSubmission（主结果）；
   - `inputVersionRefs` 必须恰好包含：
     - `FORM_SUBMISSION`：与 `resultRef`/`resultDigest` 一致；
     - `EVIDENCE_SET_SNAPSHOT`：同 Task/Project、`purpose=TASK_SUBMISSION`、digest 精确匹配；
   - 缺项、重复 kind、错误摘要或跨 Task 引用失败关闭。

同事务校验、幂等、审计、Outbox；失败不写 Task 完成态。

## 2. 契约与迁移

- OpenAPI **0.18.0**：`CompleteHumanTaskRequest.inputVersionRefs`
- 人工完成事件：`task.completed@v2`（payload 含可选 `inputVersionRefs`）；自动任务仍发 `@v1`
- Flyway **V043**：`tsk_task.input_version_refs jsonb`
- staging 期望 **043/45**

## 3. 未实现范围

1. ReviewCase / ReviewDecision / CorrectionCase；
2. purpose=`REVIEW`/`REPORT` Snapshot 完成门禁；
3. 以独立领域 Submission 聚合取代 FormSubmission 主结果；
4. ADR-018 条件槽位重解析。
