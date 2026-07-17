---
title: M186 角色与授权治理
status: Implemented
milestone: M186
lastUpdated: 2026-07-17
relatedMilestones: [M185, M187, M188]
---

# M186 角色与授权治理

## 目标

在现有 `authorization` 模块上补齐 Role/Capability 目录治理、RoleGrant 申请→批准/拒绝→撤销、
Delegation、职责分离、可授予范围、DENY 优先运行时、grant generation 失效与授权解释。

## 范围与非目标

- 范围：
  - 扩展 `authorization`（不新建 Modulith 模块）；允许 `reliability :: api`；
  - Flyway V089：角色/授权治理列、事件、委托、tenant grant generation、治理能力种子；
  - Core OpenAPI `0.79.0` 治理 HTTP API；
  - SoD、可授予范围、Delegation 子集、ACTIVE/DENY/委托合成判定；
  - PostgreSQL IT、MVC 安全测试、ArchitectureTest 证据。
- 明确不做：
  - Admin 用户中心 UI 与真实 OIDC 治理 E2E（M187）；
  - Portal `/me` contexts/navigation（M188）；
  - MFA step-up 执行器、完整义务运行时；
  - ORGANIZATION DataScope、Region 层级后代、授权缓存平台。

## 事实源

- `roadmap/03-identity-organization-governance-delivery-plan.md` §8
- `roadmap/04-identity-organization-governance-agent-worklist.md` §6
- `testing/identity-organization-governance-program-acceptance.md` §6
- `api/07-project-configuration-access-governance-http-api.md` §7–9（Proposed，仅形状参考）
- `decisions/ADR-025-role-grant-governance.md`

## 设计要点

见 ADR-025。写命令：授权 → 幂等 → 行锁/版本 → 追加事件 → 审计；撤销/批准生效时推进
grant generation。运行时 SQL 只命中 ACTIVE 且未过期未撤销的授权；匹配范围内 DENY 击败 ALLOW；
ACTIVE Delegation 按能力子集合成参与判定；`policyVersion` 绑定租户 grant generation。

## 已实现

- [x] ADR-025
- [x] OpenAPI Core `0.79.0`
- [x] Flyway `V089`
- [x] 扩展现有 `authorization` 模块（`reliability :: api`）
- [x] Role/Capability 目录与租户角色组合
- [x] RoleGrant 申请/批准/拒绝/撤销 + 追加事件
- [x] Delegation 创建/撤销与运行时合成
- [x] SoD、可授予范围、DENY 优先、grant generation
- [x] `authorization:explain`
- [x] Postgres IT + security tests + ArchitectureTest
- 扩展 `authorization` 与 `auth_` Flyway V089；
- 治理 HTTP `/api/v1/capabilities|roles|role-grants|delegations|authorization:explain`；
- `JdbcAuthorizationPolicyStore` ACTIVE/DENY/Delegation/`role-grant-v3:g{n}`；
- `OrganizationRoleGrantAdapter` 批量撤销推进 generation 并写事件；
- `RoleGrantGovernancePostgresIT`、`AuthorizationGovernanceControllerSecurityTest`。

## 明确未实现

- Network Portal 角色与授权操作面（Admin 授权操作面由 M187 承接）；
- MFA/approval obligation 执行器；
- Portal 上下文失效协议的完整 `/me` 消费（M188）；
- 跨服务授权事件总线与集中缓存。

## 工程证据

- Flyway：`db/migration/authorization/V089__create_role_grant_governance.sql`
- OpenAPI：`serviceos-core-v1.yaml` 0.79.0
- IT：`RoleGrantGovernancePostgresIT`、`AuthorizationPolicyPostgresIT`
- MVC：`AuthorizationGovernanceControllerSecurityTest`
- ArchitectureTest

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs
bash scripts/agent-verify.sh arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,AuthorizationGovernanceControllerSecurityTest,RoleGrantGovernancePostgresIT,AuthorizationPolicyPostgresIT
./mvnw -pl serviceos-contracts -am test
bash scripts/agent-verify.sh contracts
```
