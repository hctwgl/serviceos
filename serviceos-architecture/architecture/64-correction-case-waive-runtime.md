---
title: M51 CorrectionCase WAIVED 运行时
version: 0.1.0
status: Implemented
---

# M51 CorrectionCase WAIVED 运行时

## 1. 实现范围

1. `POST /correction-cases/{id}:waive`：对 `OPEN|IN_PROGRESS|RESUBMITTED` 案例进入终态 `WAIVED`；
2. 需要能力 `evidence.waiveCorrection`（CRITICAL）；必须提供 `reason` 与 `approvalRef`；
3. 同事务：写 `waived_by/waived_at/waive_approval_ref/waive_note`、审计、Outbox；若已关联整改 Task 则取消该 HUMAN Task；
4. `WAIVED` 后不可补传/关闭/再次豁免；不伪装成 `CLOSED`；
5. OpenAPI **0.25.0**；Flyway **V051**；staging **051/53**。

## 2. 未实现

条件槽位 `requiredWhen`（ADR-018）、OCR/CV、多候选人策略评分、自动 claim。
