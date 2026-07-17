---
title: M151 Admin 目录与 SLA Accepted OpenAPI 筛选补齐
status: Implemented
milestone: M151
lastUpdated: 2026-07-17
---

# M151 Admin 目录与 SLA Accepted OpenAPI 筛选补齐

## 1. 范围

承接 M150，将目录/SLA 上已 Accepted 但未接线的查询参数接到 Admin：

```text
工单目录 → projectId
任务目录 → projectId + status=SUCCEEDED
SLA 工作台 → projectId
项目目录 → activeOn
```

不新增 OpenAPI、不做 SavedView、不做入站列表。

## 2. 实现要点

1. 四个目录页补齐缺失筛选控件与 cursor 重置；
2. Playwright 在登录后依次断言四类筛选 GET 200。

## 3. 事务 / 授权 / 幂等

只读查询 UI；授权仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- 专用入站队列列表 API、SavedView、企业 OIDC/BFF；
- 工作区 TASKS 区块深链（可作为后续切片）；
- 真实 sandbox。

## 5. 证据入口

- `serviceos-admin-web/src/pages/WorkOrderDirectoryPage.vue`
- `serviceos-admin-web/src/pages/TaskDirectoryPage.vue`
- `serviceos-admin-web/src/pages/SlaQueuePage.vue`
- `serviceos-admin-web/src/pages/ProjectDirectoryPage.vue`
- `serviceos-admin-web/tests/e2e/admin-pilot-smoke.spec.ts`
- `serviceos-architecture/testing/148-m151-admin-directory-sla-filter-parity-acceptance.md`
- `ADMIN-PILOT-08DF`
