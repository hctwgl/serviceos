---
title: M135～M140 身份与组织治理 Agent 工作清单
version: 1.0.0
status: Accepted
decisionDate: 2026-07-17
---

# M135～M140 身份与组织治理 Agent 工作清单

## 1. 执行规则

1. 每次开始先读取最新 `master`、`AGENTS.md`、实施状态、本交付计划和程序级验收矩阵；不得根据旧 PR 猜基线。
2. 默认一个里程碑一个独立分支和 Draft PR；前一里程碑未合并时，不依赖其内部表继续开发。确需 stacked PR 必须得到项目负责人明确批准。
3. 开发阶段执行 L0～L2 精准本地验证并继续工作，不在每次中间 push 后等待 GitHub Actions；最终候选 HEAD 冻结后才等待一次完整门禁。
4. 不创建传统 `sys_user(password, role, dept_id)` 万能表，不用单一 `user_type` 覆盖多 Persona，不用 Keycloak Group 代替业务组织权威。
5. 不用菜单、路由、JWT role 或前端自报 scope 代替后端 RoleGrant/Membership/责任实时鉴权。
6. 不读其他模块内部表、不建立跨模块外键、不在事件或日志中泄露密码、token、完整联系方式和身份映射。
7. 所有写命令具备 Idempotency-Key、版本/ETag、审计、失败关闭和明确恢复路径。
8. Flyway 使用下一个连续版本；不得修改已发布迁移。Apple Silicon/PostgreSQL 镜像规则继续遵守根 `AGENTS.md`。

## 2. 共用交付模板

每个里程碑 PR 必须包含：

```text
架构/实现文档
数据模型与 Flyway
公共 API/端口
OpenAPI 与必要事件 Schema
PostgreSQL Testcontainers
MVC Security
Spring Modulith ArchitectureTest
审计/幂等/并发/缓存失权测试
适用 Portal 或真实 OIDC E2E
implementation-status 与 README 索引同步
明确未实现边界
```

预留实现文档编号：M135 从 `architecture/148-*` 开始；每个新里程碑使用连续编号。程序级验收矩阵不替代里程碑实现证据。

## 3. M135 Agent 清单：统一主体目录

分支建议：`feat/m135-unified-principal-directory`

- 盘点现有 `identity`、JWT principal、authorization 的稳定 principalId 使用；禁止破坏已有主体引用。
- 定义 Principal、IdentityLink、PersonProfile、Persona 和生命周期不变量。
- 决定首次登录登记策略、受信 issuer/client 和冲突绑定处置。
- 增加迁移、Repository、应用服务、公开查询/命令 API 和审计。
- 提供主体搜索/详情的最小字段与 FieldPolicy；不暴露密码、token 或不必要 IdP 属性。
- 覆盖并发绑定、跨租户 subject、禁用旧 JWT、多个 Persona 和版本冲突。
- 更新 Core OpenAPI、生成客户端、实施状态和索引。

完成后才允许 M136 依赖稳定 Principal API。

## 4. M136 Agent 清单：企业组织与任职

分支建议：`feat/m136-organization-directory`

- 通过 ADR/架构文档确定独立组织目录模块名和允许依赖。
- 实现 Organization、OrgUnit、closure、OrgMembership 和有效期历史。
- 明确 LOCAL 与 EXTERNAL_AUTHORITATIVE 字段所有权、来源键和同步收据。
- 实现组织树/搜索、成员查询、调动、离职和同步批次；禁止任意 SQL scope。
- 离职/停用联动主体失权、RoleGrant 终止/撤销和待重新分配清单。
- 覆盖循环、组织移动、乱序同步、重复批次、部分失败和跨租户父子关系。

## 5. M137 Agent 清单：网点人员与师傅身份

分支建议：`feat/m137-network-technician-directory`

- 明确 Partner Organization、ServiceNetwork 与内部 OrgUnit 的边界。
- 实现 NetworkMembership、TechnicianProfile、NetworkTechnicianMembership 和 Qualification。
- 邀请/绑定账号必须复用 M135 Principal，不创建第二套用户表。
- 候选与派单查询同时验证师傅档案、网点关系、技能/资质和状态。
- 清退/停用展示并处理 Task、Appointment、Visit、ServiceAssignment 和离线工作包影响。
- 覆盖多网点师傅、跨网点隔离、资质过期、并发邀请和旧成员缓存失权。

## 6. M138 Agent 清单：角色与授权治理

分支建议：`feat/m138-role-grant-governance`

- 复用现有 Capability 与 RoleGrant 运行时，不另建平行 RBAC。
- 实现角色模板/租户角色、授权申请/审批/撤销、Delegation 和授权解释。
- 高风险授权执行职责分离、可授予范围、MFA/approval obligations 和增强审计。
- 授权历史只追加；到期/撤销使查询 context、scope cursor 和缓存失败关闭。
- 覆盖同授权维度 AND、多授权 OR、显式拒绝、越权授予、自批和乱序撤销。

## 7. M139 Agent 清单：Admin 统一用户中心

分支建议：`feat/m139-admin-user-center`

- 建立用户、组织、合作方/网点人员、师傅、角色与授权页面。
- 所有选择器使用目录搜索，不把 principal UUID 暴露为主要交互。
- 显示来源、有效期、同步状态、影响范围、未完成工作、版本和 obligations。
- EXTERNAL_AUTHORITATIVE 字段只读；写动作提交后重读权威 API。
- 使用真实 Keycloak PKCE、Backend、PostgreSQL 和 Chrome 覆盖搜索、绑定、调动、授权、撤权和停用。
- 无权限深链和搜索不得泄露姓名、联系方式、组织或角色。

## 8. M140 Agent 清单：Portal 上下文与导航

分支建议：`feat/m140-portal-context-navigation`

- 实现 `/me`、`/me/contexts`、`/me/capabilities`、`/me/navigation`。
- 上下文只由有效 Persona、Membership、RoleGrant 和 feature gate 计算。
- 建立代码注册 Page Registry；数据库仅管理启用、标题覆盖、顺序和 feature gate。
- Admin、Network、Technician Portal 独立消费 contexts；前端切换不能扩权。
- 覆盖旧上下文版本、授权变化、网点切换、多 Persona、隐藏 URL 和伪造 allowed action。
- Schema 预留 Consumer Persona，但不得宣称 C 端注册/用户中心已交付。

## 9. PR 描述必填

```text
Milestone / baseline SHA
解决的权威事实和不变量
数据与模块所有权
API/事件/迁移变更
授权与敏感数据影响
幂等、并发、撤权和恢复证明
精准测试与最终完整门禁
明确未实现
下一里程碑允许依赖的公共表面
```

## 10. 禁止完成声明

下列情况只能标记 PARTIAL 或保持 ACCEPTED：只有设计/页面；只有 Keycloak realm 或初始化 SQL；测试使用跳过鉴权的 token；没有 PostgreSQL 证据；没有撤权/跨租户测试；仍要求手工输入 principalId；网点/师傅与账号混为一表；导航隐藏但 API 可越权；文档未同步。
