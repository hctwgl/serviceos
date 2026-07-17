---
title: M147 Admin 工作区外发交付详情深链
status: Implemented
milestone: M147
lastUpdated: 2026-07-17
---

# M147 Admin 工作区外发交付详情深链

## 1. 范围

承接 M146，在已 Implemented 的工作区 `INTEGRATION.outboundDeliveries[]` 与
`GET /outbound-deliveries/{id}` 上补齐与 M145 对称的运营深链：

```text
工单工作区 INTEGRATION
→ 深链 /integration/outbound/{deliveryId}
→ GET /outbound-deliveries/{id}
→ 展示已有外发交付安全摘要
```

不新增 OpenAPI、不改 Outbound 状态机、不做 SavedView。

## 2. 实现要点

1. `WorkOrderWorkspacePage` 解析 `outboundDeliveries[]` 生成 `RouterLink`（`ADMIN.INTEGRATION.DETAIL`）；
2. 复用已有 `OutboundDeliveryDetailPage` 与路由，不新建详情页；
3. Playwright 外发用例：ACK 后从工作区 INTEGRATION 点击深链并断言详情与 URL。

## 3. 事务 / 授权 / 幂等

本切片为只读 UI 深链：

- 工作区区块与详情 GET 仍由后端 Capability / Tenant/Project Scope 强制；
- 缺 `integration.readOutbound` 时投影 `outboundDeliveries` 为 null，UI 不渲染外发链接；
- 不引入写命令。

## 4. 明确未实现

- 专用入站队列列表 API；
- Review/Correction 队列筛选增强；
- 多 status OR、SavedView、真实 sandbox。

## 5. 证据入口

- `serviceos-admin-web/src/pages/WorkOrderWorkspacePage.vue`
- `serviceos-admin-web/src/pages/OutboundDeliveryDetailPage.vue`
- `serviceos-admin-web/tests/e2e/admin-pilot-smoke.spec.ts`
- `serviceos-architecture/testing/144-m147-admin-workspace-outbound-delivery-deeplink-acceptance.md`
- `ADMIN-PILOT-08OD`（`testing/admin-pilot-readiness-acceptance.md`）
