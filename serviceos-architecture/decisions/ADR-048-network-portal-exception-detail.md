---
title: ADR-048：Network Portal 运营异常详情只读 UI
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
  - Operations Owner
related_adrs:
  - decisions/ADR-041-network-portal-exception-queue.md
  - decisions/ADR-047-network-portal-correction-detail.md
---

# ADR-048：Network Portal 运营异常详情只读 UI

## 1. 状态与已接受决策

本 ADR 作为 M210 的边界结论，正式接受：

1. 在 M203 已 Accepted 的
   `GET /api/v1/network-portal/operational-exceptions/{exceptionId}` 之上，交付 Admin Web
   `/network-portal/exceptions/:id` **只读详情页**；
2. **不**新建 HTTP 路径、capability、Flyway 或 Page Registry pageId（仍归属
   `NETWORK.EXCEPTION.QUEUE`；catalog **保持** `page-registry-v15`）；
3. 响应契约复用既有 `NetworkPortalExceptionItem`（含 `allowedActions` 恒为空）；
4. 列表异常 ID 深链至详情；详情提供任务深链（若有 taskId）；**禁止**渲染 ACK/resolve 写控件；
5. Core OpenAPI **保持 `0.99.0`**；Flyway **仍 100/102**；
6. **不**接受：Portal ACK/resolve、通知、产能写、新 pageId。

## 2. 上下文

M203 已交付 list/get HTTP 与队列壳；Admin Web 仅消费 list。详情页提供完整安全摘要展示面，
并显式强调 Portal `allowedActions` 为空、不得一键 ACK。

## 3. 后果

- Admin Web 详情页 + 列表深链 + E2E；
- Portal ACK/resolve 若需要，须另接受切片。
