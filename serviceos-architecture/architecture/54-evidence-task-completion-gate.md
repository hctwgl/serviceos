---
title: M41 EvidenceSetSnapshot Task 完成门禁
version: 0.1.0
status: Implemented
---

# M41 EvidenceSetSnapshot Task 完成门禁

## 1. 实现边界

M41 实现资料规范中“提交与完成分离”的完成侧门禁，镜像 M35 表单模式：

- `CreateEvidenceSetSnapshot` 仍只冻结不可变成员集合，不隐式完成 Task；
- 无 `formRef` 且已解析出非空 EvidenceSlot 的 HUMAN Task 完成时，`resultRef` 必须使用
  `evidence-set-snapshot://{snapshotId}`；
- `resultDigest` 必须等于该 Snapshot 的 `contentDigest`；
- Snapshot 必须属于同一 tenant、Task、Project，且 `purpose=TASK_SUBMISSION`；
- 错误引用、错误摘要、跨 Task 或不存在的 Snapshot 以 `EVIDENCE_SET_NOT_VALIDATED`
  失败关闭；
- `formRef` 非空的表单 Task 仍由 M35 处理；本校验器直接跳过；
- 零槽位权威 Resolution、无 Resolution、非资料 Task 不改变既有通用结果引用语义。

表单与资料双引用完成条件（`inputVersionRefs`）尚未实现。

## 2. 模块边界

task 公开 `HumanTaskCompletionValidator`；evidence 实现
`EvidenceSetSnapshotTaskCompletionValidator`。task 不依赖 evidence 内部包，不访问 `evd_*` 表。

## 3. 事务与失败语义

与 M35 相同：校验在 `task.human.complete` 幂等抢占后、Task 条件更新前，处于同一完成事务。
失败回滚幂等抢占，不更新 Task / Assignment / 审计 / Outbox。

## 4. 契约

OpenAPI **0.16.0** 补充资料 Task 的 `resultRef` / `resultDigest` 语义说明。无新 Flyway 迁移。

## 5. 未实现范围

1. 表单+资料同时完成的结构化 `inputVersionRefs`；
2. Review/Correction；
3. purpose=`REVIEW`/`REPORT` 的完成门禁；
4. ADR-018 条件槽位重解析。

`evidence.invalidate` 已由 [M42](55-evidence-invalidate-runtime.md) 实现。
