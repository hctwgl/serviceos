---
title: ADR-044：Network Portal 师傅关系只读列表
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-024-network-technician-directory.md
  - decisions/ADR-032-network-portal-read-apis.md
  - decisions/ADR-042-network-portal-manage-technician.md
  - decisions/ADR-043-network-portal-qualification-list.md
---

# ADR-044：Network Portal 师傅关系只读列表

## 1. 状态与已接受决策

本 ADR 作为 M206 的边界与授权结论，正式接受：

1. Network Portal **只读查询**扩展「本网点师傅关系列表/详情」；**不**新建 portal 模块；
2. HTTP（Core OpenAPI `0.98.0`）：
   - `GET /api/v1/network-portal/technician-memberships`
   - `GET /api/v1/network-portal/technician-memberships/{membershipId}`
3. **上下文**：`X-Network-Context` 必填；**禁止** query-param `networkId`；
4. **查询参数（list）**：可选 `status`（默认 `ACTIVE`）、`technicianProfileId`、`limit`（1～100，默认 50）；
   响应复用 `NetworkPortalPage` + 含 `version` 的关系摘要（供 terminate If-Match）；
5. **能力**：不新增 capability 种子。要求 ACTIVE NetworkMembership + NETWORK scope
   `technician.readOwnNetwork`；
6. **数据**：仅返回 `serviceNetworkId = contextNetworkId` 的 NetworkTechnicianMembership；
   详情跨网点失败关闭；
7. **编排归属**：`readmodel` fan-in；`network` 提供无鉴权目录端口（同 QualificationQuery）；
8. Page Registry：保持 `NETWORK.TECHNICIAN.LIST`；catalog → `page-registry-v13`（能力列表不变，
   仅版本递增登记本切片导航语义稳定）；
9. **不**接受：操作员 NetworkMembership CRUD、Portal decide、产能申请、Admin TENANT
   membership 集合 API 原样暴露。

## 2. 上下文

M204 已交付 create/terminate（If-Match 需要真实 `version`）；M194 technicians 列表未向 Portal
HTTP 暴露 membershipVersion，导致 Admin Web 终止表单只能硬编码版本。本 ADR 窄接受专用
membership 只读面，补齐写路径并发控制。

## 3. 后果

- OpenAPI `0.97.0` → `0.98.0`；**无**新 Flyway（仍 100/102）；
- Admin Web 终止控件从 membership 列表填充真实 version；
- 操作员成员邀请等若需要，须另接受切片。
