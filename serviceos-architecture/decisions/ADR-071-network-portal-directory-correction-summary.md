---
title: ADR-071 Network Portal 目录页资料整改案例服务端摘要旁载
status: Accepted
date: 2026-07-17
milestone: M233
---

# ADR-071：Network Portal 目录页资料整改案例服务端摘要旁载（目录「整改」列 · 承接 M225 / M232）

- Status: Accepted
- Date: 2026-07-17
- Relates to: ADR-067（目录旁载程序先例 · M229）、ADR-068～070（目录 technician/appointment/contactAttempts）、ADR-065（工作台 corrections）、ADR-060（CorrectionCase 投影）、product/03 §5「资料/整改」、architecture/246、testing/230、M233

## Context

M225 已在 Network Portal 工作台提供可选 `corrections[]`（`$ref` `NetworkPortalWorkspaceCorrectionCaseSummary`，软门禁 NETWORK `evidence.read`）。M230～M232 已将技师 / 预约 / 联系尝试旁载到目录页，消除 Admin Web 目录 N+1。

产品仍要求目录「资料/整改」列（product/03 §5）。若在目录对每行 `taskIds` 再调工作台或独立 list，会再次引入 N+1。本 ADR 将 **同形** 工作台整改摘要提升为目录页级旁载，复用 M225 DTO 与查询路径。

## Decision

1. **扩展既有目录页包装**（不新 endpoint）：
   - `GET /network-portal/work-orders` → `NetworkPortalWorkOrderPage.corrections?`
   - `GET /network-portal/tasks` → `NetworkPortalTaskPage.corrections?`
2. **条目契约**：`$ref` / 运行时复用 `NetworkPortalWorkspaceCorrectionCaseSummary`（与 Admin CorrectionCaseSummary 字段对齐；不含 `createdBy` / `waiveNote`）。
3. **软门禁**：NETWORK `evidence.read`（与 M223/M225/M229 一致）。缺权时省略 `corrections`（与 `technicians`/`appointments`/`contactAttempts`/`reviews` 一致）。
4. **范围**：
   - work-orders：当前页 `items[].taskIds` 并集上全部状态案例；
   - tasks：当前页 `items[].taskId`；
   - 仅可信本网点 ACTIVE 责任任务；跨网 / 未知 id 静默跳过。
5. **排序**：同 M225（`createdAt` 升序，其次 `correctionCaseId`）。
6. **OpenAPI**：`1.0.12 → 1.0.13`；catalog 仍 v16；无 Flyway；无新 pageId。
7. **Admin Web**：目录「整改」列优先读页级 `corrections`；缺旁载时不展示（不回退 N+1）。
8. **明确未实现**：目录 SLA 风险旁载；目录 evidence slots/items；独立 NP Correction CRUD；通知中心；Portal ACK；ORGANIZATION SavedView。

## Consequences

- 目录资料/整改列可零额外 HTTP 渲染。
- 与工作台 corrections 同权、同摘要、同审计边界。
