---
title: M45 CorrectionCase 最小运行时
version: 0.1.0
status: Implemented
---

# M45 CorrectionCase 最小运行时

## 1. 批准边界

承接 M44：ReviewCase `REJECTED` 后进入整改闭环最小切片。同步扩展 ADR-008 接受范围至 CorrectionCase 只追加补传轮次。

## 2. 实现范围

1. ReviewCase 追加 `REJECTED` 决定时，同事务自动创建 `CorrectionCase`（`OPEN`）；
2. 每个 `ReviewDecision` 至多一个 CorrectionCase；
3. 补传：`POST ...:resubmit` 追加不可变补传轮次，状态 `OPEN|RESUBMITTED → RESUBMITTED`；
4. 关闭：`POST ...:close` 仅允许 `RESUBMITTED → CLOSED`（表示本轮整改已复核关闭，不等于审核通过）；
5. Capability：查询 `evidence.read`；补传 `evidence.submit`；关闭 `evidence.review`；
6. 同事务：幂等、审计、Outbox；
7. OpenAPI **0.20.0**；Flyway **V045**；staging **045/47**。

## 3. API

- `GET /api/v1/correction-cases/{correctionCaseId}`
- `POST /api/v1/correction-cases/{correctionCaseId}:resubmit`
- `POST /api/v1/correction-cases/{correctionCaseId}:close`

## 4. 事件

- `evidence.correction-case-created@v1`
- `evidence.correction-resubmitted@v1`
- `evidence.correction-closed@v1`

## 5. 未实现

~~整改 Task 自动创建 / IN_PROGRESS~~（M47）；~~`WAIVED`~~（M51）；强制通过、重开、车企回执已在后续里程碑。
