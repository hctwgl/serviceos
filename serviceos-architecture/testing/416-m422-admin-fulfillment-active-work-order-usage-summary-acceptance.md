---
title: M422 Admin 履约配置中心使用中工单摘要验收矩阵
version: 0.1.0
status: Implemented
milestone: M422
lastUpdated: 2026-07-21
---

# M422 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | soft-omit | 缺 `workOrder.read` 时 count/truncatedated 均为 null | `ProjectFulfillmentProfilePostgresIT` |
| A2 | 计数 | 有能力时返回项目 ACTIVE 工单数 | 同上 |
| A3 | 硬门禁 | 缺 `project.fulfillment.read` → ACCESS_DENIED | 既有 list/compare 鉴权路径 + 本接口同 require |
| A4 | 模块边界 | ArchitectureTest | ArchitectureTest |
| A5 | Admin UI | SummaryStrip 展示服务端计数，无「待服务端计数」 | Playwright |

产品状态：`READY_FOR_REVIEW`。
