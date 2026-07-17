---
title: ADR-040：Network Portal 整改队列只读查询
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
  - Evidence Owner
related_adrs:
  - decisions/ADR-001-modular-monolith-first.md
  - decisions/ADR-013-reference-engineering-profile-and-module-enforcement.md
  - decisions/ADR-026-portal-context-navigation-in-authorization.md
  - decisions/ADR-032-network-portal-read-apis.md
  - decisions/ADR-039-network-portal-evidence-on-behalf.md
---

# ADR-040：Network Portal 整改队列只读查询

## 1. 状态与已接受决策

本 ADR 作为 M202 的边界与授权结论，正式接受：

1. Network Portal **只读查询**扩展「本网点整改队列」；**不**新建 portal 模块、**不**新建并行整改投影；
2. HTTP（Core OpenAPI `0.94.0`）：
   - `GET /api/v1/network-portal/correction-cases`
   - `GET /api/v1/network-portal/correction-cases/{correctionCaseId}`
3. **上下文**：`X-Network-Context` 必填（同 ADR-032）；**禁止** query-param `networkId`；
4. **查询参数（list）**：`status`（默认 `OPEN`）、可选 `taskId`、`limit`（1～100，默认 50）；
   **不**接受 Admin 队列的 projectId / sourceReviewCaseId / cursor 绑定项目范围语义
   （本切片按 ACTIVE NETWORK 任务集合内存收敛，响应复用 `NetworkPortalPage` 形态）；
5. **能力**：不新增 capability 种子。要求 ACTIVE NetworkMembership + NETWORK scope
   `evidence.read`；缺能力 `ACCESS_DENIED`；伪造/非成员 → `PORTAL_CONTEXT_INVALID`；
6. **数据**：仅返回其 `taskId` 属于该网点 ACTIVE NETWORK `ServiceAssignment` 的
   CorrectionCase 安全摘要（复用 `CorrectionCaseQueueItem` 字段语义）；详情额外校验
   任务 ACTIVE NETWORK 责任 = 上下文网点；
7. **编排归属**：`readmodel` fan-in（同 ADR-032）；经 `dispatch::api` /
   `evidence::api` / `network::api`；
8. Page Registry：`NETWORK.CORRECTION.QUEUE`（catalog → `page-registry-v9`）；
9. **不**接受：Admin `GET /correction-cases` 直接当 Portal 用、资质/产能申请写、
   异常队列、离线工作包、完整 product/03 设计系统。

## 2. 上下文

M201 已交付资料代补写命令，但缺少本网点整改发现面。Admin M98 整改队列是项目范围
fan-in，不能安全暴露给 Network Portal。本 ADR 窄接受 API-06 §10 增补一条整改只读路径。

## 3. 后果

- OpenAPI 从 `0.93.0` 升至 `0.94.0`；**无**新 Flyway（仍 099/101）；
- Admin Web `/network-portal/corrections` 列表并深链任务页代补控件；
- cursor/项目范围 Admin 队列语义、资质写与产能申请若需要，须另接受切片。
