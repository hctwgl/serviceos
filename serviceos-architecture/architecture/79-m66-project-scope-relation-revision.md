---
title: M66 项目范围关系整组修订
status: Implemented
lastUpdated: 2026-07-15
---

# M66 项目范围关系整组修订

## 1. 目标

M66 补齐 M64～M65 只在创建项目时写入 REGION/NETWORK 关系的治理缺口。授权管理员可以在项目创建后，
以一次显式整组命令替换项目当前 REGION 与 NETWORK 关系；被删除关系只结束有效期，新增关系只追加，
历史事实不可覆盖。

本切片不实现 ServiceNetwork、Region 或 Organization 目录，也不从名称、地址、ServiceAssignment、
Coverage 或登录主体猜测关系。两个关系集合都必须在请求中显式给出，空数组表示终止该类型全部当前关系。

## 2. 契约与并发

- `POST /api/v1/projects/{projectId}:revise-scope-relations` 要求 `Idempotency-Key`、双引号正整数
  `If-Match` 和受信 JWT；
- 请求必须同时携带 `regionCodes`、`networkIds` 和非空 `reason`，不支持缺字段表示“保持不变”；
- 引用沿用 M64/M65 的数量、空白、长度、重复与稳定排序规则；
- `If-Match` 精确匹配 Project `aggregate_version`，写入使用条件更新；并发陈旧命令返回
  `VERSION_CONFLICT`，不能部分结束或新增关系；
- 整组内容与当前集合完全相同时返回明确冲突，不伪造一次成功修订。

Core OpenAPI 以加法方式新增端点和请求 Schema。修订成功响应不可变
`ProjectScopeRelationRevision` 收据并返回新 ETag；幂等重放按 revisionId 读取首次冻结收据，不能用项目
后续当前状态冒充首次结果。

## 3. 数据与事务

V066 增加：

- `project.reviseScopeRelations` HIGH capability；
- `prj_project_scope_revision` 不可变修订收据，冻结完整集合、差异、原因和新版本；
- REGION/NETWORK 当前开放关系的部分唯一索引，数据库层阻止同一项目同一引用出现两个开放区间；
- `ended_by`、`ended_at`，并约束结束审计字段与 `valid_to` 同时出现。

命令事务顺序为：授权 → 幂等抢占 → 锁定/校验 Project 版本 → 条件递增聚合版本 → 结束被移除关系 →
追加新增关系 → Audit → Outbox → 幂等完成。`valid_to`、`ended_at` 和新增关系 `valid_from` 使用同一个
服务端 `Clock` 时刻；未变化关系保持原始有效区间。

## 4. 事件与授权投影

新增不可变 `project.scope-relations-revised@v1`，包含修订后的完整集合、added/removed 差异、原因、
新聚合版本和发生时间。事件不包含客户端声明的 tenant 或 actor。

M64/M65 的 resolver 继续按有效期实时读取关系。因此命令提交后：

- 被移除 REGION/NETWORK grant 不再解析到该项目；
- 新增关系立即进入精确项目集合；
- SLA 跨项目游标中的 scope digest 变化，旧游标失败关闭。

## 5. 明确未实现

- ServiceNetwork 目录、准入/启用/清退状态、合同、停派、Coverage、Capability、Qualification；
- Region/Organization 目录、层级后代和组织关系；
- 未来生效的计划修订、审批工作流、双人复核和治理 UI；
- 项目 owners、激活/退役、服务产品绑定修订；
- 授权缓存、导出、Network Portal、派单策略、BUSINESS SLA 与结算。

## 6. 工程证据

- `ProjectCommandPostgresIT`：整组修订、关系历史、幂等冻结重放、陈旧版本、无变化、清空集合、
  授权拒绝、不可变收据与数据库唯一约束；
- `AuthorizationPolicyPostgresIT`、`SlaClockPostgresIT`：关系修订后授权解析即时变化，旧 scope digest
  游标失败关闭；
- `ProjectControllerSecurityTest`、`ProjectTest`、`DefaultProjectCommandServiceTest`：HTTP 契约、显式字段、
  If-Match、领域校验与事务编排；
- `ArchitectureTest`：Spring Modulith 边界通过；
- PostgreSQL 18.4 Testcontainers：68 个迁移成功执行到 V066；
- Core OpenAPI 0.37.0、`project-scope-relations-revised-v1.schema.json`、契约兼容和客户端可重复生成门禁通过；
- `bash scripts/verify-local.sh` 全量 L3 通过。
