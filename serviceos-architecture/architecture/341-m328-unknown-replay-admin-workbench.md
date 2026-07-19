---
title: M328 UNKNOWN / Replay Admin 工作台
status: Implemented
milestone: M328
lastUpdated: 2026-07-19
relatedMilestones: [M59, M318, M319, M327]
---

# M328 UNKNOWN / Replay Admin 工作台

## 目标

将已实现的 M318 人工处置与 M319 批量 ReplayRequest API 接入 Admin Portal，
形成可操作的 UNKNOWN Delivery 运营工作台切片。

## 范围与非目标

- 范围：
  - 详情页：`MANUAL_CONFIRMED` / `ABANDONED` → `POST …:record-manual-ack`
  - 队列页：勾选 UNKNOWN → PREVIEW / SUBMIT → APPROVE / REJECT
  - Capability 软门禁（`recordManualOutboundAck` / `batchReplayUnknownDelivery` /
    `retryUnknownDelivery`）；后端仍失败关闭
  - 既有单笔 `:retry` 保留
- 明确不做：
  - 新 Page Registry 导航项 / OpenAPI / Flyway
  - 二级审批 / MFA / 1000 条压测
  - Delivery GET `allowedActions`
  - 吉利 Sandbox

## 已实现

- Admin UI 接入 + 文档/状态同步

## 明确未实现

- 完整运营设计系统、批量压测、自动改状态、跨租户

## 验证命令

```bash
cd serviceos-admin-web && npm run build
# 后端既有证据（本切片不改后端）：
bash scripts/agent-verify.sh it ManualDispositionPostgresIT,BatchReplayPostgresIT
```
