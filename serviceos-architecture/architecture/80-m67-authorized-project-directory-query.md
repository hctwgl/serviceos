---
title: M67 项目授权目录与范围历史查询
status: Implemented
lastUpdated: 2026-07-15
---

# M67 项目授权目录与范围历史查询

## 1. 目标

M67 为 `ADMIN.PROJECT.LIST` 补齐首个可运行的项目只读纵向切片。获授 `project.read` 的主体可以按
TENANT/PROJECT/REGION/NETWORK RoleGrant 实时解析项目集合，查询项目目录、单项目当前事实和 M66
不可变范围修订历史。

本切片只暴露已经由 Project 聚合、有效期关系和修订收据证明的事实，不把尚未实现的品牌、owners、
服务产品绑定、生命周期动作或 ServiceNetwork/Region/Organization 目录伪装成空值或默认值。

## 2. HTTP 契约

- `GET /api/v1/projects`：授权范围内项目目录；支持精确 `clientId`、受控 `status`、服务端解释的
  `activeOn`、稳定 cursor 和 `1..100` limit；
- `GET /api/v1/projects/{projectId}`：项目当前核心事实和当前 REGION/NETWORK 关系，返回聚合 ETag；
- `GET /api/v1/projects/{projectId}/scope-revisions`：按 `aggregateVersion` 倒序分页读取不可变修订收据；
- 所有查询要求受信 JWT 和 `project.read`，tenant、principal 和授权项目集合不接受客户端覆盖；
- 列表 cursor 绑定实时 scope digest 与全部筛选条件，授权关系或筛选变化后旧 cursor 明确失败；
- `activeOn` 仅执行 `startsOn <= activeOn <= endsOn`（无 endsOn 视为开放区间），不推断项目状态。

列表以不可变 `projectCode`、`projectId` 排序。当前关系通过关系表读取，不从创建事件、修订 JSON、
ServiceAssignment 或工单反推。

## 3. 授权与失败关闭

- 集合查询复用 `ProjectScopeAuthorizationService`，TENANT 表示租户全部项目，PROJECT/REGION/NETWORK
  映射取并集；无有效范围时拒绝并写拒绝审计；
- 详情和历史先按 tenant 查询资源，缺失统一 404，再按资源冻结的 projectId 实时执行
  `project.read`，撤权立即生效；
- 不允许只有 JWT capability 字符串却没有数据库 RoleGrant 的主体读取；
- 非法 status、limit、cursor 或超长 clientId 返回明确校验错误，不回退为无筛选查询。

## 4. 数据与性能

V067 增加 NORMAL 风险 `project.read` capability，以及 tenant + 稳定项目代码的目录游标索引。项目列表
使用一次范围化 SQL 查询，不能逐行调用授权服务或 N+1 查询关系。详情读取当前关系；历史分页直接读取
`prj_project_scope_revision`，不修改不可变事实。

本里程碑没有业务写事务、Outbox 或新领域事件；读取使用只读事务，并返回服务端 `asOf` 便于 Portal
标识投影时点。

## 5. 明确未实现

- Project owners、品牌、服务产品绑定、配置发布绑定及其查询；
- Project 激活、停单、暂停、关闭、退役等生命周期命令；
- ServiceNetwork、Region、Organization 目录与层级；
- 计划生效的范围修订、审批、双人复核和治理 UI；
- 项目全文检索、模糊匹配、导出、保存视图和分析指标；
- Admin Portal 前端工程。

## 6. 工程证据

- `ProjectQueryService` 与 `DefaultProjectQueryService`：范围解析、校验、scope/filter cursor 绑定和只读事务；
- `ProjectQueryRepository`、`ProjectQueryMapper.xml`：授权集合进入单条 MyBatis SQL，当前关系在同一查询聚合；
- `V067__add_authorized_project_query.sql`：`project.read` capability 与 tenant 目录游标索引；
- Core OpenAPI 0.38.0：目录、详情和范围修订历史三个 GET 契约及生成客户端；
- `ProjectQueryPostgresIT`：PostgreSQL 18 的 TENANT/REGION/PROJECT 范围、筛选、分页、撤权、拒绝审计、
  跨租户 404、历史与迁移证据；
- `ProjectControllerSecurityTest`、`ContractValidationTest`、`ArchitectureTest` 以及全量 L3 门禁。
