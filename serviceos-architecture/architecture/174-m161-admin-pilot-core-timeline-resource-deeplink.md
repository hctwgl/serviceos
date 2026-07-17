---
title: M161 Admin 核心时间线资源详情深链
status: Implemented
milestone: M161
lastUpdated: 2026-07-17
---

# M161 Admin 核心时间线资源详情深链

## 1. 范围

承接 M153 明确留待的「权威核心时间线表格内嵌链接」，并扩展 TIMELINE_AUDIT 白名单至
已 Implemented 的表单/资料详情页：

```text
工单工作区权威核心时间线 items[]
→ allow-listed resourceType
→ 已有 Admin 详情页 GET

TIMELINE_AUDIT 白名单同步扩展 FormSubmission / EvidenceItem / EvidenceSetSnapshot
```

不新增 OpenAPI、后端读契约或业务状态语义。

## 2. 实现要点

1. `TIMELINE_RESOURCE_ROUTES` 增加 FormSubmission / EvidenceItem / EvidenceSetSnapshot；
2. 核心时间线旁链「打开核心时间线资源」；标签前缀 `core /` 避免 Playwright strict 冲突；
3. Playwright `ADMIN-PILOT-08CT`：完结后核心时间线 → FormSubmission / EvidenceSetSnapshot GET 200。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；详情 GET 仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- FieldOperation 详情、SavedView、企业 OIDC/BFF、ServiceNetwork；
- 时间线表格单元格内嵌超链接（当前为表格旁链，语义等价）。

## 5. 证据入口

- `WorkOrderWorkspacePage.vue`
- `admin-pilot-smoke.spec.ts`
- `testing/158-m161-admin-pilot-core-timeline-resource-deeplink-acceptance.md`
