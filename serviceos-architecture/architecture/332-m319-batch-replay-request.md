---
title: M319 批量 ReplayRequest 预演与审批
status: Implemented
milestone: M319
lastUpdated: 2026-07-19
relatedMilestones: [M59, M318]
---

# M319 批量 ReplayRequest 预演与审批

## 目标

为 UNKNOWN OutboundDelivery 提供批量预演/提交审批入口；批准后逐条复用单笔 `:retry` 语义，带限流。

## 范围

- Flyway **V120**：`int_batch_replay_request` / `int_batch_replay_item` + capability `integration.batchReplayUnknownDelivery`
- `POST /replay-requests`（mode=PREVIEW|SUBMIT）
- `GET /replay-requests/{batchId}`
- `POST /replay-requests/{batchId}:approve`（APPROVE|REJECT）
- 资格：UNKNOWN、无人工处置、project scope；默认 max 20、硬上限 50
- APPROVE 调度需同时具备 `integration.retryUnknownDelivery`
- Core OpenAPI **1.0.43**

## 明确未实现

- 1000 条级预演压测；二级审批/MFA；跨租户批量；吉利 Sandbox

## 验证

```bash
bash scripts/agent-verify.sh it BatchReplayPostgresIT
bash scripts/agent-verify.sh contracts
```
