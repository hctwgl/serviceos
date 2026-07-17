---
title: ADR-030：Admin 受控全局搜索与能力门禁
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Admin Portal Owner
related_adrs:
  - decisions/ADR-001-modular-monolith-first.md
  - decisions/ADR-013-reference-engineering-profile-and-module-enforcement.md
  - decisions/ADR-025-role-grant-governance.md
  - decisions/ADR-027-admin-personal-saved-view.md
---

# ADR-030：Admin 受控全局搜索与能力门禁

## 1. 状态与已接受决策

本 ADR 作为 M192 的边界与授权结论，正式接受：

1. Admin 受控搜索由 `readmodel` 编排（薄 fan-in 服务），**不**新建 search 模块，**不**物化
   `search_document` 索引表或异步 indexer；
2. HTTP：`GET /api/v1/search?q=&types=`；Portal 仅 `ADMIN`；
3. 本切片 type：`WORK_ORDER`、`EXTERNAL_ORDER`、`NETWORK`、`TECHNICIAN`；请求未支持 type
   （含 `VEHICLE`/`CHARGER`）失败关闭为 `SEARCH_TERM_NOT_ALLOWED`（422）；
4. **能力**：入口需要 `search.read`（HIGH）；各 type 另需 underlying 读能力
   （`workOrder.read` / `network.read`；师傅工号精确另经 `identity.read` 端口）。
   缺 type 能力时**省略该 type**（降级），缺 `search.read` 则整请求 `ACCESS_DENIED`（403）；
5. 搜索只复用既有授权查询端口与 ScopePredicate；**不**授予额外数据或命令能力；
6. 手机号形态仅允许末四位；响应/日志不回显完整敏感 `q`（仅 digest/掩码）；
7. `readmodel` 允许依赖 `workorder::api`、`network::api`、`identity::api`、
   `authorization::api`（已有）以完成 fan-in。

## 2. 上下文

API-06 §7 / DATA-06 §9 长期草案允许「索引不可用时回退受控精确查询」。SavedView/偏好轨道
（M189～M191）完成后，运营需要跨资源入口，但不得引入未经验证的全文索引平台或扩大敏感检索面。

## 3. 后果

- ArchitectureTest 验证 `readmodel → network::api`（及既有依赖）；
- 本地 fixture 须授予 `search.read`（及 underlying 读能力）方可 E2E 命中；
- `VEHICLE`/`CHARGER`、全文索引、Network/Technician Portal 搜索若未来需要，须另接受切片。
