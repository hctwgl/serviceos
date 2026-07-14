---
title: M40 EvidenceSetSnapshot 运行时
version: 0.1.0
status: Implemented
---

# M40 EvidenceSetSnapshot 运行时

M40 在 M39 `VALIDATED` Revision 之上实现不可变资料集合冻结，覆盖 EVD-008 / EVD-009 的最小闭环。

本里程碑实现既有领域设计，不新增 ADR。

## 1. 能力

- `POST /api/v1/tasks/{taskId}/evidence-set-snapshots`
- `GET /api/v1/evidence-set-snapshots/{snapshotId}`

Capability：创建用 `evidence.submit`，查询用 `evidence.read`。主体必须是 RUNNING HUMAN Task
的责任人，且无 ACTIVE ExecutionGuard。

## 2. 成员资格（TASK_SUBMISSION）

1. `memberRevisionIds` 非空且无重复；
2. 每个 Revision 属于当前 Task、落在已解析 Slot 内；
3. 状态必须是 `VALIDATED`；
4. 同一 Snapshot 同一 Item 至多一个 Revision；
5. 各 Slot `minCount` / `maxCount` 约束满足；
6. 任一失败整体拒绝，不产生 Snapshot / Member 行。

后续补传不会改变已创建 Snapshot；新提交创建新 Snapshot。

## 3. 冻结事实

- Snapshot：`purpose`、`resolutionId`、`memberCount`、`contentDigest`、`eligibilitySummary`；
- Member：slot/item/revision、`revisionNumber`、`revisionStatus`、`contentDigest`、
  `validationDigest`、稳定 `memberOrdinal`；
- 触发器禁止 UPDATE/DELETE。

## 4. 事件与数据

- 事件：`evidence.set-snapshotted@v1`
- OpenAPI **0.15.0**
- V041：`evd_evidence_set_snapshot`、`evd_evidence_set_member`
- staging 期望 **041/43**

## 5. 明确未实现

1. purpose=`REVIEW`/`REPORT`/`EXTERNAL_DELIVERY`；
2. 服务端自动挑选最新 VALIDATED Revision；
3. ReviewCase / CorrectionCase；
4. 内容摘要完全相同的 Snapshot ID 复用。

Task 完成门禁引用 Snapshot 已由 [M41](54-evidence-task-completion-gate.md) 实现。
`evidence.invalidate` 已由 [M42](55-evidence-invalidate-runtime.md) 实现；作废不得改写本里程碑 Snapshot。
