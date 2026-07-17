---
title: ADR-031：Admin 最近访问与读时重鉴权
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Admin Portal Owner
related_adrs:
  - decisions/ADR-001-modular-monolith-first.md
  - decisions/ADR-013-reference-engineering-profile-and-module-enforcement.md
  - decisions/ADR-027-admin-personal-saved-view.md
  - decisions/ADR-028-admin-ui-preferences.md
  - decisions/ADR-030-admin-controlled-search.md
---

# ADR-031：Admin 最近访问与读时重鉴权

## 1. 状态与已接受决策

本 ADR 作为 M193 的边界与授权结论，正式接受：

1. Admin 最近访问由 `readmodel` 拥有；物理表 `rdm_recent_resource`；**不**新建模块；
2. HTTP：`GET /api/v1/me/recent-resources` 与 `PUT /api/v1/me/recent-resources`（upsert/touch）；
   Portal 仅 `ADMIN`；
3. 本切片 resourceType：`WORK_ORDER`、`TASK`、`PROJECT`、`NETWORK`、`TECHNICIAN`；
4. **能力**：读写本人最近访问**不**要求新 capability（与 SavedView/UiPreference 一致）；
   列表项经既有授权查询端口（workorder/task/project/network）重新鉴权；失权项**省略**，
   不整列表 403，并可在读路径删除陈旧行；
5. touch 为 authenticated upsert；同 `(tenant, principal, portal, type, id)` 更新
   `lastVisitedAt`；列表上限 20；
6. `displayRef` 仅为非敏感短标签，不得保存完整电话/地址/价格；
7. `readmodel` 允许依赖 `project::api`（及既有 workorder/task/network/identity/authorization）
   以完成读时重鉴权；
8. **不**接受 `GET /me/notifications`、`GET /me/application-context`（本切片）；
   **不**接受 Network/Technician Portal 最近访问。

## 2. 上下文

API-06 §3 / DATA-06 §8 草案定义最近访问以改善运营导航。M188 已交付 `/me` 导航，
M189～M192 完成偏好/搜索轨道；运营需要「最近打开」快捷入口，但不得把历史访问当授权凭证，
也不得缓存敏感展示快照。

## 3. 后果

- ArchitectureTest 验证 `readmodel → project::api`；
- Admin Web 在关键详情页 touch，并在 AppShell 展示最近列表深链；
- 通知、workbench、work-queues、跨 Portal 最近访问若未来需要，须另接受切片。
