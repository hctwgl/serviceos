---
title: ADR-026：Portal 上下文与导航归属 authorization 模块
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Identity Domain Owner
related_adrs:
  - decisions/ADR-001-modular-monolith-first.md
  - decisions/ADR-013-reference-engineering-profile-and-module-enforcement.md
  - decisions/ADR-025-role-grant-governance.md
---

# ADR-026：Portal 上下文与导航归属 authorization 模块

## 1. 状态与已接受决策

本 ADR 作为 M188 的 Modulith 边界结论，正式接受：

1. `/me`、`/me/contexts`、`/me/capabilities`、`/me/navigation` 由现有模块 `authorization`
   拥有；**不**新建独立 `portal` Modulith 模块；
2. Page Registry 的稳定 `pageId` / `routeKey` / required capabilities 以代码注册为权威；
   数据库仅保存租户级启用、标题覆盖、排序与 feature gate（`auth_page_registry_override`、
   `auth_feature_gate`）；
3. 上下文只由有效 Persona、Membership、RoleGrant 与 feature gate 计算；请求中的
   `contextId` / `projectId` / `networkId` 只能选择服务端已返回项；
4. `contextVersion` 绑定租户 grant generation；旧版本失败关闭；
5. 导航可见不是授权事实；业务 API 仍按资源重新鉴权；
6. Schema 预留 CONSUMER Persona，但不产生 Portal 上下文或导航入口，也不宣称 Consumer Identity
   已交付。

## 2. 上下文

M183～M187 已提供 Principal、组织任职、网点/师傅与 RoleGrant 治理。交付计划要求三 Portal
独立消费服务端上下文，且前端切换不能扩权。新建 portal 模块会额外引入依赖方向评审，而
authorization 已依赖 `identity::api` / `organization::api` / `network::api`，并拥有
Capability 与 grant generation。

## 3. 后果

- ArchitectureTest 继续验证 `authorization` 边界，无需扩大其他模块 `allowedDependencies`；
- identity/organization/network 仅通过最小只读 Affiliation/Persona 查询端口供上下文合成；
- Admin Web 与 Network/Technician 最小 stub 独立调用 `/me*`，不得共享含数据范围假设的 store。
