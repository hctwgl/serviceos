---
title: M137 Admin BYD 提审外发 ACK E2E
status: Implemented
milestone: M137
lastUpdated: 2026-07-16
---

# M137 Admin BYD 提审外发 ACK E2E

## 1. 范围

在真实 Keycloak / Backend / PostgreSQL / Chrome 冒烟中证明：

```text
INTERNAL APPROVED
→ createBydReviewSubmission（获权 USER）
→ Task worker HTTP（本地 stub errno=0）
→ DELIVERED → ACKNOWLEDGED + CLIENT ReviewCase
```

本地 stub 仅证明协议严格 ACK，不宣称真实 sandbox。

## 2. 关键决策

- 获权 `USER` 与 `SERVICE` 均可创建提审交付（Capability 失败关闭）；修正 M118 Admin 按钮此前无法用用户 JWT 调用的问题。
- Core OpenAPI **0.71.0**。

## 3. 工程证据

- `DefaultOutboundDeliveryService`、`grant-local-project-admin.sql`（含 BYD adapter）
- `seed-admin-byd-lineage.sql`、本地 stub、`verify-admin-smoke.sh`
- Playwright outbound 用例；`testing/134-m137-*`
