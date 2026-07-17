---
title: M181 Admin QueueTable 行内单元格深链
status: Implemented
milestone: M181
lastUpdated: 2026-07-17
---

# M181 Admin QueueTable 行内单元格深链

## 1. 范围

承接 M176～M180，为 `QueueTable` 增加可选 `linkColumns`，仅在显式映射时将
UUID 列渲染为已有 Admin 详情路由：

```text
QueueTable 单元格（opt-in）→ 已有 ADMIN.*.DETAIL / WORKSPACE
```

默认仍为明文，避免 Attempt / Milestone 等非资源表误链。
不新增 OpenAPI；不发明多态 `sourceId` / FieldOperation 路由。

## 2. 实现要点

1. `QueueTable.vue`：`linkColumns` + `.queue-cell-link`；
2. Review/Correction/Outbound/Exception/Inbound/SLA/目录页首批接入；
3. `uuidRoute` 抽到 `routeQuery.ts`；
4. Playwright `ADMIN-PILOT-08QL`：审核队列表格 `projectId` 单元格 → Project GET 200。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；目标 GET 仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- 移除下方关联资源条、SavedView、FieldOperation、企业 OIDC/BFF。

## 5. 证据入口

- `QueueTable.vue` / 各队列与目录页 / `routeQuery.ts`
- `admin-pilot-smoke.spec.ts`
- `testing/178-m181-admin-pilot-queuetable-cell-deeplink-acceptance.md`
