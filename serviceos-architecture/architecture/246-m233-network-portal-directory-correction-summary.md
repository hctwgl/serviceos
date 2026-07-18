---
title: M233 Network Portal 目录页资料整改案例服务端摘要
status: Implemented
milestone: M233
lastUpdated: 2026-07-17
relatedMilestones: [M225, M229, M232]
---

# M233：Network Portal 目录页资料整改案例服务端摘要旁载（ADR-071）

> 状态：Implemented（实现见本里程碑；验收见 testing/230）

## 1. 目标与边界

| 项 | 内容 |
| --- | --- |
| 目标 | 目录 work-orders/tasks 页包装可选 `corrections[]`，消除 Admin Web 目录「整改」列 N+1 |
| 非目标 | 目录 SLA 风险；目录 evidence；独立 NP Correction API；通知；Portal ACK |
| 依赖 | M225、M232、ADR-071 |
| OpenAPI | 1.0.12 → **1.0.13**；catalog v16 |

## 2. 契约

- Schema：`NetworkPortalWorkOrderPage` / `NetworkPortalTaskPage` 增加可选 `corrections`（`$ref` `WorkOrderWorkspaceCorrectionCaseSummary`）。
- Path 描述：声明软门禁与 taskId 范围。

## 3. 运行时

- `NetworkPortalPage` 增加 `corrections`；convenience 构造补齐。
- `DefaultNetworkPortalQueryService.listWorkOrders` / `listTasks`：在 `EVIDENCE_READ` 软门禁下，对页级 taskIds 调用既有 `loadCorrectionSummaries`。
- 与 M225 工作台共用装载路径（全状态、本网页任务、排序一致）。

## 4. Admin Web

- `networkPortal.ts`：`NetworkPortalPage` 增加 `corrections?`。
- `NetworkPortalWorkOrdersPage.vue` / `NetworkPortalTasksPage.vue`：新增「整改」列；按 taskId 匹配页级摘要；缺旁载不请求。

## 5. 测试

- IT：缺权省略；有权空数组；按页 taskIds 装载；跨网跳过。
- E2E：`network-portal-directory-correction-summary.spec.ts`。

## 6. 明确未实现

目录 SLA 风险服务端摘要；目录 evidence 旁载；独立 NP Review/Correction；通知中心；Portal ACK；ORGANIZATION SavedView。

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh contracts
bash scripts/verify-local.sh
```
