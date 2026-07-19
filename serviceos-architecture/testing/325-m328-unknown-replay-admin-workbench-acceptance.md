---
title: M328 UNKNOWN / Replay Admin 工作台验收矩阵
status: Implemented
milestone: M328
lastUpdated: 2026-07-19
---

# M328 UNKNOWN / Replay Admin 工作台验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M328-01 | 详情页人工确认 | 调用 `:record-manual-ack` MANUAL_CONFIRMED；展示 disposition | Admin UI + ManualDispositionPostgresIT |
| M328-02 | 详情页放弃 | ABANDONED；禁止再次 retry（后端） | 同上 |
| M328-03 | 队列批量 PREVIEW/SUBMIT | 调用 `/replay-requests`；展示 items | Admin UI + BatchReplayPostgresIT |
| M328-04 | 批次 APPROVE/REJECT | 调用 `:approve` | 同上 |
| M328-05 | Capability 软隐藏 | 无能力时隐藏处置/批量入口 | `capabilitiesGate` |
| M328-06 | Admin 构建 | `npm run build` | CI / 本地 |

## 明确不验收

- MFA、新导航注册、OpenAPI 变更、吉利联调
