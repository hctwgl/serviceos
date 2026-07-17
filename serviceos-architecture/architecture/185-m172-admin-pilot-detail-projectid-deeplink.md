---
title: M172 Admin 详情页明文 projectId 深链
status: Implemented
milestone: M172
lastUpdated: 2026-07-17
---

# M172 Admin 详情页明文 projectId 深链

## 1. 范围

承接 M157 / M164 / M168 / M171，将详情页仍以明文展示的 `projectId` 升级为
`ADMIN.PROJECT.DETAIL` RouterLink，避免运营在已有授权详情上再手工复制 UUID：

```text
审核详情 / 异常详情 / Canonical Message / 外发交付
→ projectId → /projects/{id}
```

不新增 OpenAPI；不发明 FieldOperation / ReviewRoute / SavedView。

## 2. 实现要点

1. `ReviewCaseDetailPage`：`projectId` 字段与「打开项目」链接；
2. `ExceptionDetailPage` / `CanonicalMessageDetailPage`：可空 `projectId` 条件深链；
3. `OutboundDeliveryDetailPage`：`projectId` 字段与交叉链接区；
4. Playwright 在审核详情断言 `ADMIN-PILOT-08PJ`（GET `/projects/{id}` 200）。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；项目详情 GET 仍由后端 `project.read` 与 Tenant/Project Scope 强制。

## 4. 明确未实现

- FieldOperation / ReviewRoute 详情、SavedView、企业 OIDC/BFF、真实 sandbox。

## 5. 证据入口

- `ReviewCaseDetailPage.vue` / `ExceptionDetailPage.vue` /
  `CanonicalMessageDetailPage.vue` / `OutboundDeliveryDetailPage.vue`
- `admin-pilot-smoke.spec.ts`
- `testing/169-m172-admin-pilot-detail-projectid-deeplink-acceptance.md`
