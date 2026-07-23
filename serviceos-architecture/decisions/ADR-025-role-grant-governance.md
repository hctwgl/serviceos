---
title: ADR-025：RoleGrant 治理扩展现有 authorization 模块
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Identity Domain Owner
related_adrs:
  - decisions/ADR-001-modular-monolith-first.md
  - decisions/ADR-013-reference-engineering-profile-and-module-enforcement.md
  - decisions/ADR-023-organization-directory-module.md
---

# ADR-025：RoleGrant 治理扩展现有 authorization 模块

## 1. 状态与已接受决策

本 ADR 作为 RoleGrant 治理的 Modulith 依赖评审结论，正式接受：

1. Role、稳定 Capability、RoleGrant、Delegation、授权解释与治理命令由现有模块
   `authorization` 拥有，表前缀保持 `auth_`；**不**新建独立 Modulith 模块；
2. `authorization` 允许依赖 `shared`、`identity::api`、`audit::api`、`organization::api`、
   `network::api` 与 `reliability::api`（幂等门禁）；
3. Capability 是全局稳定安全契约，租户不得重定义语义或风险级别；租户角色只能组合目录能力；
4. 高风险 RoleGrant 执行职责分离与可授予范围校验；授权历史只追加；撤销/到期立即使运行时失权，
   并通过租户 grant generation 使依赖策略版本的上下文失败关闭；
5. `auth_*` 表不对 `org_` / `idn_` / `net_` 表建立物理外键，仅保存稳定主体/范围引用。

## 2. 上下文

Principal、组织任职与网点/师傅目录已经存在。实时 RoleGrant 运行时已经存在，
但缺少申请/审批/撤销、Delegation、职责分离、授权解释与治理 HTTP API。交付计划要求复用现有
Capability/RoleGrant 运行时，不另建平行 RBAC。

## 3. 决策细节

### 3.1 治理不变量

- 申请人不能批准自己的 HIGH/CRITICAL RoleGrant（`ROLE_GRANT_DUTY_CONFLICT`）；
- 审批者必须对目标角色全部 Capability 持有覆盖同范围或更广范围的 ACTIVE ALLOW
  （`ROLE_GRANT_ESCALATION_FORBIDDEN`）；
- Delegation 的能力、范围与期限不得超过委托人（`DELEGATION_SCOPE_TOO_BROAD`）；
- 运行时只认 ACTIVE、未撤销、未过期的 RoleGrant；匹配范围内 DENY 优先于 ALLOW；
- ACTIVE Delegation 作为委托人能力子集的合成授权参与判定。

### 3.2 不拥有

- Admin 用户中心页面与真实 OIDC 治理；
- Portal `/me` 上下文与导航；
- MFA step-up 执行器（本切片仅保留义务位与审计钩子边界，不伪造完成）。

## 4. 后果

- ArchitectureTest 继续验证 `authorization` 模块边界，并允许 `reliability :: api`；
- Organization 任职终止撤权继续经 `OrganizationRoleGrantPort`，并在撤销时推进 grant generation；
- 各 Portal 消费治理 API 与实时判定，不得复制 RoleGrant 规则。
