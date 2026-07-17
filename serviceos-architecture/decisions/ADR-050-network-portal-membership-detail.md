---
title: ADR-050：Network Portal 师傅关系详情只读 UI
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-044-network-portal-technician-memberships.md
  - decisions/ADR-049-network-portal-qualification-detail.md
---

# ADR-050：Network Portal 师傅关系详情只读 UI

## 1. 状态与已接受决策

本 ADR 作为 M212 的边界结论，正式接受：

1. 在 M206 已 Accepted 的
   `GET /api/v1/network-portal/technician-memberships/{membershipId}` 之上，交付 Admin Web
   `/network-portal/technicians/memberships/:id` **只读详情页**；
2. **不**新建 HTTP 路径、capability、Flyway 或 Page Registry pageId（仍归属
   `NETWORK.TECHNICIAN.LIST`；catalog **保持** `page-registry-v15`）；
3. 响应契约复用既有 `NetworkPortalMembershipItem`（含真实 `version` / terminate*）；
4. 师傅列表关系 ID 深链至详情；终止写控件仍留在师傅列表页（既有 M204/M206）；
5. Core OpenAPI **保持 `0.99.0`**；Flyway **仍 100/102**；
6. **不**接受：操作员 NetworkMembership、Portal decide、新 pageId。

## 2. 上下文

M206 已交付 list/get HTTP；师傅页消费 list 以填充 terminate version，但缺少独立详情展示面。

## 3. 后果

- Admin Web 详情页 + 列表深链 + E2E；
- 写路径继续走师傅列表表单。
