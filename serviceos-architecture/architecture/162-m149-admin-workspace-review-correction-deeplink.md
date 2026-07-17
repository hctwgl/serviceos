---
title: M149 Admin 工作区审核/整改详情深链
status: Implemented
milestone: M149
lastUpdated: 2026-07-17
---

# M149 Admin 工作区审核/整改详情深链

## 1. 范围

承接 M148，在已 Implemented 的工作区 `REVIEWS_CORRECTIONS` 投影上补齐与 M145/M147 对称的运营深链：

```text
工单工作区 REVIEWS_CORRECTIONS
→ reviews[] → /reviews/{reviewCaseId}
→ corrections[] → /corrections/{correctionCaseId}
```

不新增 OpenAPI、不改变 Review/Correction 状态机。

## 2. 实现要点

1. `WorkOrderWorkspacePage` 解析 `reviews[]` / `corrections[]` 生成 `RouterLink`；
2. 复用已有 `ReviewCaseDetailPage` / `CorrectionCaseDetailPage`；
3. Playwright 整改豁免用例：驳回后从工作区点击审核与整改深链。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；区块与详情 GET 仍由后端 Capability / Scope 强制；缺权时数组为 null，不渲染链接。

## 4. 明确未实现

- 专用入站队列列表 API、SavedView、企业 OIDC/BFF；
- 运营异常队列筛选（可作为后续切片）；
- 真实 sandbox。

## 5. 证据入口

- `serviceos-admin-web/src/pages/WorkOrderWorkspacePage.vue`
- `serviceos-admin-web/tests/e2e/admin-pilot-smoke.spec.ts`
- `serviceos-architecture/testing/146-m149-admin-workspace-review-correction-deeplink-acceptance.md`
- `ADMIN-PILOT-08RD`
