---
title: ADR-043：Network Portal 本网点资质只读列表
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
---

# ADR-043：Network Portal 本网点资质只读列表

## 1. 状态与已接受决策

本 ADR 作为 M205 的边界与授权结论，正式接受：

1. Network Portal **只读查询**扩展「本网点师傅资质列表/详情」；**不**新建 portal 模块；
2. HTTP（Core OpenAPI `0.97.0`）：
   - `GET /api/v1/network-portal/technician-qualifications`
   - `GET /api/v1/network-portal/technician-qualifications/{qualificationId}`
3. **上下文**：`X-Network-Context` 必填；**禁止** query-param `networkId`；
4. **查询参数（list）**：可选 `status`、`technicianProfileId`、`limit`（1～100，默认 50）；
   响应复用 `NetworkPortalPage` + 资质安全摘要字段；
5. **能力**：不新增 capability 种子。要求 ACTIVE NetworkMembership + NETWORK scope
   `technician.readOwnNetwork`；
6. **数据**：仅返回其 `technicianProfileId` 对本网点持有 ACTIVE
   `NetworkTechnicianMembership` 的资质；详情同样失败关闭跨网点；
7. **编排归属**：`readmodel` fan-in；`network` 提供无鉴权目录端口
   （调用方负责 Portal 门禁，同 `NetworkPortalTechnicianQuery`）；
8. Page Registry：扩展 `NETWORK.QUALIFICATION` 含 `technician.readOwnNetwork`
   （catalog → `page-registry-v12`）；
9. **不**接受：Portal `:decide`、FileObject、产能申请、Admin TENANT `network.read` 原样暴露。

## 2. 上下文

M204 已交付资质 PENDING 提交，但缺少本网点资质发现/到期浏览面。Admin
`GET /technician-profiles/{id}/qualifications` 依赖 TENANT `network.read`，不能安全给 Portal 用。

## 3. 后果

- OpenAPI `0.96.0` → `0.97.0`；**无**新 Flyway（仍 100/102）；
- Admin Web `/network-portal/qualifications` 列表页；
- decide / FileObject 若需要，须另接受切片。
