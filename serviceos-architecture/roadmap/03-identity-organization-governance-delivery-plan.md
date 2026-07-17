---
title: M135～M140 统一身份、组织与授权治理交付计划
version: 1.0.0
status: Accepted
decisionDate: 2026-07-17
---

# M135～M140 统一身份、组织与授权治理交付计划

## 1. 决策

ServiceOS 正式接受 M135～M140 作为 M134 之后的连续实施序列，用于把现有 OIDC/JWT、Capability、RoleGrant 和范围授权底座扩展为可运营的统一主体目录、企业组织、网点人员、师傅身份、角色授权治理和多 Portal 上下文。

本计划是实施承诺，不是完成声明。只有对应代码、Flyway、OpenAPI/事件契约、PostgreSQL 与安全测试、Portal E2E 和实施状态证据全部成立后，单个里程碑才能从 `ACCEPTED` 改为 `IMPLEMENTED`。

## 2. 不变量

1. ServiceOS 不保存登录密码、密码摘要、MFA 秘钥或 IdP 会话；认证仍由 Keycloak/企业 IdP 负责。
2. `Principal` 是稳定操作主体；自然人、服务账号、登录身份、人员档案、组织关系、业务身份和授权不得混成一张万能用户表。
3. 一名自然人可以绑定多个外部身份、拥有多个 Persona，并在不同有效期和范围拥有不同 RoleGrant。
4. 内部组织、合作商、ServiceNetwork 和 TechnicianProfile 是不同业务对象；网点不能伪装成内部部门，师傅档案不能等同登录账号。
5. 菜单、路由和按钮不是授权事实。服务端命令继续按 Capability、DataScope、当前责任、状态、版本和 obligations 实时判定。
6. 用户停用、离职、转岗、网点清退和师傅关系失效必须失败关闭，并生成待重新分配工作，不得只隐藏菜单。
7. 所有身份绑定、组织变更、授权申请/审批/撤销、代办和敏感查看均保留增强审计。
8. 内部员工组织可由 HR/OA/AD/企业微信等外部主数据源同步，也可使用 LOCAL 权威模式；两种模式必须显式，不做静默双向覆盖。

## 3. 领域所有权

| 事实 | 权威边界 |
|---|---|
| OIDC issuer/subject、Principal、IdentityLink、PersonProfile、Persona、账号状态 | `identity` |
| 企业/合作组织、内部 OrgUnit、closure、人员任职与来源同步 | 独立组织目录边界；实施前通过 Modulith 依赖评审确定模块名 |
| Role、稳定 Capability、RoleGrant、DataScope、Delegation、授权决定 | `authorization` |
| ServiceNetwork、NetworkMembership、TechnicianProfile、网点师傅关系、技能与资质 | 服务网络边界；不得由 identity 或 authorization 直接拥有 |
| 页面注册、Portal 上下文和导航投影 | 应用/Portal 边界；只消费公开查询，不成为授权权威 |

跨模块只保存稳定 ID，通过公开 API、查询端口和事件协作；禁止直接连接其他模块内部表或建立跨模块物理外键。

## 4. 正式里程碑

| 里程碑 | 目标 | 主要交付 | 退出条件 |
|---|---|---|---|
| M135 | 统一主体目录 | Principal、IdentityLink、PersonProfile、Persona、登录后绑定、主体生命周期和安全查询 | 一个主体可绑定多个身份并拥有多个 Persona；停用实时失权；不保存密码；PostgreSQL/安全/契约证据通过 |
| M136 | 企业组织与任职 | Organization、OrgUnit、closure、OrgMembership、LOCAL/EXTERNAL_AUTHORITATIVE 来源和同步收据 | 组织树、历史任职、调动/离职和外部同步乱序可验证；离职触发撤权与待办重分配 |
| M137 | 网点人员与师傅身份 | Partner Organization、ServiceNetwork 目录、NetworkMembership、TechnicianProfile、NetworkTechnicianMembership、Qualification、邀请绑定 | 网点与部门边界明确；师傅多网点关系、资质和停用影响可验证；跨网点读取失败关闭 |
| M138 | 角色与授权治理 | Role/Capability 目录、RoleGrant 申请/审批/撤销、Delegation、职责分离、授权解释和审计 | 不能越权授予；高风险授权不可自批；授权变化使旧上下文/游标失效；历史只追加 |
| M139 | Admin 统一用户中心 | 用户目录、组织树、合作组织/网点人员、师傅、角色和授权治理页面 | Admin 通过真实 OIDC 完成搜索、绑定、组织变更、授权和停用；页面不接受原始 principalId 作为主要操作方式 |
| M140 | Portal 上下文与导航 | `/me`、可用 contexts、capabilities、navigation；多 Persona/组织/项目/网点上下文和代码注册 Page Registry | 服务端计算 Portal 上下文；前端不能自报 network/project 扩权；导航变化不削弱业务 API 鉴权；三 Portal 可独立接入 |

## 5. M135：统一主体目录

### 5.1 最小模型

```text
security_principal
identity_link
person_profile
principal_persona
principal_lifecycle_event
```

`identity_link` 至少以 `(tenantId, issuer, subject)` 唯一；账号名、手机号和邮箱不能替代稳定 subject。首次登录的自动登记必须受租户/客户端策略控制，未知 issuer、冲突绑定和跨租户 subject 失败关闭。

### 5.2 最小 API

```text
GET  /api/v1/security-principals
GET  /api/v1/security-principals/{id}
GET  /api/v1/security-principals/{id}/identities
POST /api/v1/security-principals/{id}/identity-links
POST /api/v1/security-principals/{id}:disable
POST /api/v1/security-principals/{id}:enable
```

普通调用方只获得最小展示信息；完整身份绑定、联系方式和生命周期历史使用独立 Capability 与字段策略。

## 6. M136：企业组织与人员任职

### 6.1 模型

```text
organization
org_unit
org_unit_closure
org_membership
directory_sync_batch
directory_sync_item
```

`OrgMembership` 使用有效期和只追加历史，支持主职、兼职和负责人关系。组织移动必须重建 closure 并保留旧结构审计，不得通过字符串路径猜测下级权限。

### 6.2 主数据模式

- `LOCAL`：ServiceOS 是组织和任职权威，可通过 Admin 治理。
- `EXTERNAL_AUTHORITATIVE`：HR/OA/AD/企业微信等是权威；ServiceOS 保存可用投影、来源键、同步批次和冲突结果，外部管理字段在 UI 只读。
- 不实施未定义的双向同步；需要回写时另行接受 Connector/ADR。

## 7. M137：网点人员与师傅身份

### 7.1 模型

```text
partner_organization
service_network
network_membership
technician_profile
network_technician_membership
technician_qualification
```

`TechnicianProfile` 与 `Principal` 一对一或显式受控关联，但生命周期独立；账号有效不代表可接单。可接单至少要求主体、师傅档案、当前网点关系、技能/资质和服务状态全部有效。

网点负责人只能邀请和维护授权网点范围内成员；总部负责网点准入、负责人、高风险停用和资质最终审核。清退前必须展示未完成 Task、Appointment、Visit 和离线工作包影响。

## 8. M138：角色与授权治理

`Capability` 是全局稳定安全契约，不能由租户重定义语义。租户可以创建角色并组合能力，但所有 RoleGrant 必须包含主体、角色、组织/项目/区域/网点范围、有效期、来源、原因和审批信息。

正式命令包括申请、批准、拒绝、撤销和委托；高风险能力应用职责分离、MFA/approval obligations 和可授予范围校验。授权历史只追加，不能覆盖原授权区间。

## 9. M139：Admin 统一用户中心

Admin 最小页面：

```text
用户目录
企业组织架构与任职
合作组织与网点人员
师傅档案、关系与资质
角色与 Capability
用户授权、临时授权与撤销
身份绑定、停用影响与审计
```

页面搜索和选择使用安全目录 API，不要求运营人员复制 UUID。所有写动作展示影响范围、未完成工作、版本、原因和审批 obligations，并在成功后重新读取权威结果。

## 10. M140：Portal 上下文与导航

最小查询：

```text
GET /api/v1/me
GET /api/v1/me/contexts
GET /api/v1/me/capabilities
GET /api/v1/me/navigation
```

上下文由有效 Persona、Membership 和 RoleGrant 计算，至少支持 ADMIN、NETWORK、TECHNICIAN。请求中的 projectId/networkId 只能选择服务端已返回上下文，不能创建授权。

页面使用代码注册的稳定 `pageId`、route 和 required capabilities；数据库可管理启用、标题覆盖、排序和 feature gate，但不保存任意前端组件路径，也不替代后端权限。

## 11. C 端边界

Consumer Identity/CustomerProfile 属于 M140 之后的独立正式 Epic，目标已接受，但在手机号/微信登录、隐私同意、客户主数据权威、车辆/地址关系和注销保留策略确认前不分配实施里程碑。

M135～M140 必须保证模型不阻碍 C 端：Principal 支持 Consumer Persona，IdentityLink 不假设企业账号，资源授权不依赖企业组织树。不得在本序列中用 `ROLE_CUSTOMER` 直接开放全部客户工单。

## 12. 顺序和依赖

```text
M135 Principal
  → M136 Organization
  → M137 Network/Technician identity
  → M138 Authorization governance
  → M139 Admin user center
  → M140 Portal context/navigation
```

后续里程碑可以在前一里程碑 Draft PR 上评审设计，但默认不得在前一里程碑合并前形成依赖未稳定内部表的实现。任何跨模块接口变化先合并公共契约和架构测试。

## 13. 全局完成定义

- 代码、Flyway、OpenAPI/事件 Schema、生成客户端和自动化测试形成证据；
- PostgreSQL Testcontainers 覆盖唯一性、有效期、乱序、并发和撤权；
- MVC Security 覆盖跨租户、跨组织、跨网点和越权授予；
- Spring Modulith ArchitectureTest 证明模块边界；
- Admin/Network/Technician 适用 E2E 使用真实 OIDC，不用跳过后端鉴权的测试口令；
- 敏感日志、导出、审计和缓存失权门禁通过；
- `implementation-status.md` 只在证据成立后逐里程碑改为 IMPLEMENTED；
- 未实施的 Consumer、正式 HR/企业微信 Connector 和生产企业 IdP 不得被文档或 UI 宣称完成。
