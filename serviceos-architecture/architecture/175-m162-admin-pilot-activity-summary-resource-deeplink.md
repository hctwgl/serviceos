---
title: M162 Admin 最近活动资源详情深链
status: Implemented
milestone: M162
lastUpdated: 2026-07-17
---

# M162 Admin 最近活动资源详情深链

## 1. 范围

承接 M161，在已 Implemented 的 `GET /work-orders/{id}/activity-summary`（M93）上，
为具备 Admin 详情页的 `resourceType` 补齐运营深链：

```text
工单工作区「最近活动」items[]
→ allow-listed resourceType（与核心时间线 / TIMELINE_AUDIT 同构）
→ 已有 Admin 详情页 GET
```

不新增 OpenAPI、后端读契约或业务状态语义。

## 2. 实现要点

1. 复用 `TIMELINE_RESOURCE_ROUTES` / `collectTimelineResourceLinks`；
2. 旁链文案「打开最近活动资源」；标签前缀 `activity /` 避免 Playwright strict 冲突；
3. Playwright `ADMIN-PILOT-08AS`：固定 Pilot 工单最近活动 → Task GET 200。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；activity-summary 与详情 GET 仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- 关键事件 taxonomy/过滤、FieldOperation 详情、SavedView、企业 OIDC/BFF。

## 5. 证据入口

- `WorkOrderWorkspacePage.vue`
- `admin-pilot-smoke.spec.ts`
- `testing/159-m162-admin-pilot-activity-summary-resource-deeplink-acceptance.md`
