---
title: ADR-073 Network Portal 目录页资料 Evidence 服务端摘要旁载
status: Accepted
date: 2026-07-17
milestone: M235
---

# ADR-073：Network Portal 目录页资料 Evidence 服务端摘要旁载（目录「资料」列 · 承接 M223 / M234）

- Status: Accepted
- Relates to: ADR-061（工作区 Evidence）、ADR-071/072（目录旁载程序与 foreshadow）、product/03 §5「资料/整改」、architecture/248、testing/232、M235

## Context

product/03 目录默认列含「资料/整改」。M233 已交付整改旁载；M223 已在工作台提供 `evidenceSlots`/`evidenceItems`。
目录若再按行 fan-in 工作台会引入 N+1。本 ADR 将同形 Evidence 摘要提升为目录页级旁载。

## Decision

1. 扩展 work-orders/tasks 可选 `evidenceSlots` / `evidenceItems`（`$ref` Admin/NP 工作区摘要）。
2. 软门禁 NETWORK `evidence.read`；缺权时**同时**省略两属性（与 M223 一致）；与 `corrections` 同权可并存。
3. 范围：本页 taskIds；复用 `loadEvidenceSlotSummaries` / `loadEvidenceItemSummaries`。
4. OpenAPI `1.0.14 → 1.0.15`；catalog v16；无 Flyway；无新 pageId。
5. Admin Web：目录「资料」列读页级旁载；缺旁载不 N+1。
6. **明确未实现**：缩略图/下载、Revision 图、definition JSON、独立 NP Evidence API、notifications、Portal ACK、用户脱敏列。

## Consequences

- 目录「资料/整改」列对可闭合。
- 与工作台 Evidence 同权、同摘要边界。
