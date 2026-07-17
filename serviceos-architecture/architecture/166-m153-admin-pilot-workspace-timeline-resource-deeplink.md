---
title: M153 Admin 工作区 TIMELINE_AUDIT → 资源详情深链
status: Implemented
milestone: M153
lastUpdated: 2026-07-17
---

# M153 Admin 工作区 TIMELINE_AUDIT → 资源详情深链

## 1. 范围

承接 M152，在已 Implemented 的工作区 `TIMELINE_AUDIT` 投影上，为具备 Admin 详情页的
`resourceType` 补齐运营深链：

```text
工单工作区 TIMELINE_AUDIT
→ items[]（allow-listed resourceType）
→ /tasks|reviews|corrections|integration/outbound|exceptions|sla|work-orders/{resourceId}
```

不新增 OpenAPI，不新增详情页，不猜测 Appointment/Visit/Form/Evidence 等无对等面的链接。

## 2. 实现要点

1. `WorkOrderWorkspacePage` 解析 `sectionData.timeline.items[]`，按白名单映射路由；
2. 文案为 `eventType / resourceType / resourceCode`；同资源去重；
3. 本地 Pilot 种子将 `resource_type` 纠偏为 OpenAPI PascalCase（`WorkOrder` / `Task`）；
4. Playwright：固定 Pilot 工单加载 `TIMELINE_AUDIT` 后点击 Task 深链，断言详情 GET 200。

## 3. 白名单

| resourceType | 路由 |
|---|---|
| WorkOrder | `ADMIN.WORKORDER.WORKSPACE` |
| Task | `ADMIN.TASK.DETAIL` |
| ReviewCase | `ADMIN.REVIEW.DETAIL` |
| CorrectionCase | `ADMIN.CORRECTION.DETAIL` |
| OutboundDelivery | `ADMIN.INTEGRATION.DETAIL` |
| OperationalException | `ADMIN.EXCEPTION.DETAIL` |
| SlaInstance | `ADMIN.SLA.DETAIL` |

## 4. 事务 / 授权 / 幂等

只读 UI 深链；区块与详情 GET 仍由后端 Capability / Scope 强制；未知或无详情页的
`resourceType` 不渲染链接。

## 5. 明确未实现

- Appointment / Visit / FormSubmission / EvidenceItem 独立详情页（后续 M155～M160 已分别补齐对应详情页）；
- 权威「核心时间线」表格内嵌链接（M161 已以表格旁链同构实现）；
- 专用入站队列列表 API、SavedView、企业 OIDC/BFF；
- 真实 sandbox。

## 6. 证据入口

- `serviceos-admin-web/src/pages/WorkOrderWorkspacePage.vue`
- `serviceos-deploy/admin-pilot/seed-admin-pilot.sql`
- `serviceos-admin-web/tests/e2e/admin-pilot-smoke.spec.ts`
- `testing/150-m153-admin-pilot-workspace-timeline-resource-deeplink-acceptance.md`
- `ADMIN-PILOT-08TL`
