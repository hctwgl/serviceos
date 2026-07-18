---
title: M237 Network Portal 工作台统计时间展示
status: Implemented
milestone: M237
lastUpdated: 2026-07-17
relatedMilestones: [M194, M207, M208, M220, M224]
---

# M237 Network Portal 工作台统计时间展示

## 目标

闭合 product/03 §4：工作台「当前在途量」展示业务类型的同时展示统计时间；并与产能页对齐展示
容量行 `updatedAt`。

## 范围与非目标

- 范围：ADR-075；Admin Web `NetworkPortalWorkbenchPage` 渲染既有 `asOf` / `capacity[].updatedAt`；
  OpenAPI 仍 1.0.16；catalog 仍 v16；Flyway 仍 100/102；E2E。
- 明确不做：新 HTTP、今日/明日预约计数、签约比例/评分、PII、notifications、Portal ACK、产能申请。

## 事实源

- `product/03-network-portal-spec.md` §4
- `decisions/ADR-075-network-portal-workbench-stats-time.md`
- Core OpenAPI `NetworkPortalWorkbenchView` / `NetworkPortalCapacityItem`（既有）

## 设计要点

- UI-only：字段已在 M194/M207 契约与 `networkPortal.ts` 类型中存在；仅补展示与 testid。
- 页级 `asOf` = 产品「统计时间」；行级 `updatedAt` = 产能计数刷新时间（与 `/capacity` 一致）。

## 已实现

- [x] ADR-075
- [x] 工作台页级「统计时间：{{ asOf }}」
- [x] 容量行「更新时间 {{ updatedAt }}」
- [x] E2E `network-portal-workbench-stats-time.spec.ts`

## 明确未实现

今日/明日预约与上门计数、签约比例/评分、客户 PII、notifications、Portal ACK/decide、产能申请。

## 工程证据

- OpenAPI 仍 1.0.16；Flyway 仍 100/102；catalog 仍 `page-registry-v16`
- Admin Web E2E + `bash scripts/verify-local.sh`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
