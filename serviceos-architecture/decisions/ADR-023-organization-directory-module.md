---
title: ADR-023：独立 organization 组织目录模块
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Identity Domain Owner
related_adrs:
  - decisions/ADR-001-modular-monolith-first.md
  - decisions/ADR-013-reference-engineering-profile-and-module-enforcement.md
---

# ADR-023：独立 organization 组织目录模块

## 1. 状态与已接受决策

本 ADR 作为 organization 模块的 Modulith 依赖评审结论，正式接受以下决策：

1. 企业组织与任职使用独立 Spring Modulith 模块 `organization`，表前缀 `org_`；
2. `organization` 允许依赖 `shared`、`identity::api`、`reliability::api`、`audit::api`；
3. `authorization` 允许额外依赖 `organization::api`，以实现组织命令授权与任职终止撤权端口；
4. 合作组织、ServiceNetwork、TechnicianProfile 不属于本模块，由 `network` 独立边界承接。

## 2. 上下文

交付计划要求“企业/合作组织、内部 OrgUnit、closure、人员任职与来源同步”落在独立组织目录边界，
并在实施前完成模块名与依赖方向评审。工程蓝图已预留 `organization` 模块编码；稳定
`Principal`，使任职可以引用主体而不创建第二套用户表。

若把组织树放进 `identity` 或 `authorization`，会混淆登录主体、授权判定与主数据同步职责，并迫使
后续网点/师傅身份继续耦合错误边界。

## 3. 决策细节

### 3.1 模块职责

`organization` 拥有：

- `Organization`、`OrgUnit`、`org_unit_closure`；
- `OrgMembership` 有效期与只追加历史；
- `LOCAL` / `EXTERNAL_AUTHORITATIVE` 权威模式、来源键与同步批次/明细收据；
- 离职产生的待重新分配工作清单。

`organization` 不拥有：

- 密码/OIDC 绑定（`identity`）；
- Role/Capability/RoleGrant 治理申请审批（`authorization`）；
- Partner Organization、ServiceNetwork、TechnicianProfile（`network`）。

### 3.2 跨模块协作

| 协作 | 方向 | 说明 |
|---|---|---|
| 组织命令鉴权 | `organization.api.OrganizationAuthorizationPort` ← `authorization` 实现 | 复用实时 RoleGrant，不让 organization 反向依赖 authorization.api |
| 任职终止撤权 | `organization.api.OrganizationRoleGrantPort` ← `authorization` 实现 | 终止有效 RoleGrant，写入撤销审计字段 |
| 任职终止停用主体 | `identity.api.PrincipalEmploymentLifecyclePort` ← `identity` 实现 | 组织命令授权后同事务停用；不要求调用方持有 `identity.manageLifecycle` |

跨模块只保存稳定 UUID/字符串 ID，禁止跨模块物理外键或读取对方内部表。

### 3.3 权威模式

- `LOCAL`：ServiceOS 是组织和任职权威，Admin 可直接治理；
- `EXTERNAL_AUTHORITATIVE`：外部 HR/OA/AD 等是权威；普通 Admin 写结构/任职失败关闭，除非持有
  `organization.overrideExternal`；同步批次可写入并留下逐项收据。

不实施未定义的双向回写；需要回写时另行接受 Connector/ADR。

## 4. 后果

- ArchitectureTest 必须把 `organization` 纳入模块清单并验证依赖方向；
- `network` 不得把网点伪装成 `OrgUnit` 进入内部 closure；
- RoleGrant 可引用 `organizationId`，但组织树权威仍属 `organization`。
