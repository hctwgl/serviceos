---
title: ADR-042：Network Portal 师傅关系与资质提交适配器
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-001-modular-monolith-first.md
  - decisions/ADR-013-reference-engineering-profile-and-module-enforcement.md
  - decisions/ADR-024-network-technician-directory.md
  - decisions/ADR-026-portal-context-navigation-in-authorization.md
  - decisions/ADR-032-network-portal-read-apis.md
  - decisions/ADR-034-network-portal-assign-technician.md
---

# ADR-042：Network Portal 师傅关系与资质提交适配器

## 1. 状态与已接受决策

本 ADR 作为 M204 的边界与授权结论，正式接受：

1. Network Portal **写命令**扩展「本网点师傅关系绑定/终止 + 资质 PENDING 提交」；
   **不**新建 portal 模块、**不**新建并行资质审核状态机；
2. HTTP（Core OpenAPI `0.96.0`）：
   - `POST /api/v1/network-portal/technician-memberships`
   - `POST /api/v1/network-portal/technician-memberships/{membershipId}:terminate`
   - `POST /api/v1/network-portal/technician-qualifications`
3. **请求体**：
   - create：`{ technicianProfileId, validFrom }` — **禁止**客户端 `networkId`；
   - terminate：`{ reason }` + `If-Match`；
   - submit：`{ technicianProfileId, qualificationCode, validFrom, validTo? }`；
4. **上下文**：`X-Network-Context` 必填；服务端强制 `networkId = contextNetworkId`；
5. **前置失败关闭**：
   - ACTIVE NetworkMembership，否则 `PORTAL_CONTEXT_INVALID`；
   - create：TechnicianProfile ACTIVE；同网点重复 ACTIVE 关系失败关闭（沿用 M185）；
   - terminate：membership 属于上下文网点；
   - submit：师傅对本网点持有 ACTIVE `NetworkTechnicianMembership`；状态恒为 `PENDING`；
6. **能力**：
   - 种子 `networkPortal.manageTechnician`（HIGH）作 Portal 门禁（NETWORK scope）；
   - 委托 M185 命令时，底层 `network.manageTechnician` 按 **NETWORK scope** 鉴权
     （TENANT 授权仍覆盖；Admin TENANT 路径不变）；
   - 产品文档名 `technician.manageOwnNetwork` / `qualification.manageOwnNetwork`
     映射到上述能力，**不**另建同义种子；
7. **授权端口**：扩展 `NetworkAuthorizationPort` 支持 NETWORK capability；
   以请求线程局部网络范围（同 M196 Dispatch 模式）收窄 `DefaultNetworkCommandService.begin`；
8. **编排归属**：`network` 模块 Portal 写适配器；复用 `NetworkCommandService`；
9. Page Registry：`NETWORK.QUALIFICATION` + 扩展 `NETWORK.TECHNICIAN.LIST`
   （catalog → `page-registry-v11`）；
10. **不**接受：Portal `:decide` / HQ 审批、FileObject 资质材料、createTechnicianProfile、
    操作员 inviteNetworkMember、产能申请、跨网点关系。

## 2. 上下文

M185 已交付师傅目录与 Admin HTTP；M194 交付本网点师傅只读列表；product/03 §10 要求网点
维护本网点师傅关系并提交待审资质。不得让客户端自报 networkId，也不得让网点自批资质。

## 3. 后果

- OpenAPI `0.95.0` → `0.96.0`；Flyway V100 种子 `networkPortal.manageTechnician`（100/102）；
- Admin Web 网点师傅页增加绑定/终止/提交资质控件；
- FileObject 与 Portal decide 若需要，须另接受切片。
