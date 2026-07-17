---
title: ADR-041：Network Portal 运营异常队列只读查询
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
  - Operations Owner
related_adrs:
  - decisions/ADR-001-modular-monolith-first.md
  - decisions/ADR-013-reference-engineering-profile-and-module-enforcement.md
  - decisions/ADR-026-portal-context-navigation-in-authorization.md
  - decisions/ADR-032-network-portal-read-apis.md
  - decisions/ADR-040-network-portal-correction-queue.md
---

# ADR-041：Network Portal 运营异常队列只读查询

## 1. 状态与已接受决策

本 ADR 作为 M203 的边界与授权结论，正式接受：

1. Network Portal **只读查询**扩展「本网点运营异常队列」；**不**新建 portal 模块、
   **不**新建并行异常投影；
2. HTTP（Core OpenAPI `0.95.0`）：
   - `GET /api/v1/network-portal/operational-exceptions`
   - `GET /api/v1/network-portal/operational-exceptions/{exceptionId}`
3. **上下文**：`X-Network-Context` 必填（同 ADR-032）；**禁止** query-param `networkId`；
4. **查询参数（list）**：`status`（默认 `OPEN`）、可选 `taskId`、`severity`、`limit`（1～100，默认 50）；
   响应复用 `NetworkPortalPage` 形态；**不**移植 Admin cursor/项目范围集合语义；
5. **能力**：不新增 capability 种子。要求 ACTIVE NetworkMembership + NETWORK scope
   `operations.exception.read`（映射 product/03 `exception.readAssigned`）；
6. **数据**：仅返回其 `taskId` 属于该网点 ACTIVE NETWORK `ServiceAssignment` 的
   OperationalException 安全摘要；详情校验 ACTIVE NETWORK 责任 = 上下文网点；
7. **解决路径**：本切片 **不**接受 Portal `:acknowledge` / `:resolve`；`allowedActions`
   对 Portal 响应固定为空（解决须调用既有指派/代补/预约等领域动作，见 product/03 §12）；
8. **编排归属**：`readmodel` fan-in；`operations` 提供 `listForTask` 并在 get/listForTask
   鉴权请求中携带 ACTIVE NETWORK id（同 M201 CorrectionCase）；
9. Page Registry：`NETWORK.EXCEPTION.QUEUE`（catalog → `page-registry-v10`）；
10. **不**接受：资质写、产能申请、Admin 队列原样暴露、完整 product/03 设计系统。

## 2. 上下文

M202 交付整改发现面；product/03 §12 要求网点可见本网点相关异常且不得仅点“已处理”。
Admin M29/M100 异常工作台是项目范围 fan-in，不能安全暴露给 Network Portal。

## 3. 后果

- OpenAPI `0.94.0` → `0.95.0`；**无**新 Flyway（仍 099/101）；
- `operations` 模块增加 `dispatch::api` 依赖以解析 ACTIVE NETWORK；
- Admin Web `/network-portal/exceptions` 列表并深链任务页；
- Portal ACK/关闭若需要，须另接受切片。
