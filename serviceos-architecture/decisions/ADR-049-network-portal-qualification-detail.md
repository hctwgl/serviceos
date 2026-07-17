---
title: ADR-049：Network Portal 资质详情只读 UI
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-043-network-portal-qualification-list.md
  - decisions/ADR-048-network-portal-exception-detail.md
---

# ADR-049：Network Portal 资质详情只读 UI

## 1. 状态与已接受决策

本 ADR 作为 M211 的边界结论，正式接受：

1. 在 M205 已 Accepted 的
   `GET /api/v1/network-portal/technician-qualifications/{qualificationId}` 之上，交付
   Admin Web `/network-portal/qualifications/:id` **只读详情页**；
2. **不**新建 HTTP 路径、capability、Flyway 或 Page Registry pageId（仍归属
   `NETWORK.QUALIFICATION`；catalog **保持** `page-registry-v15`）；
3. 响应契约复用既有 `NetworkPortalQualificationItem`（含 decided*/decisionReason/version）；
4. 列表资质 ID 深链至详情；**禁止** Portal decide/approve 写控件；
5. Core OpenAPI **保持 `0.99.0`**；Flyway **仍 100/102**；
6. **不**接受：Portal decide、FileObject、新 pageId。

## 2. 上下文

M205 已交付 list/get HTTP 与列表壳；Admin Web 仅消费 list。详情页展示裁决字段与 version。

## 3. 后果

- Admin Web 详情页 + 列表深链 + E2E；
- Portal decide 若需要，须另接受切片。
