---
title: M65 项目网点关系与 NETWORK SLA 队列
status: Implemented
lastUpdated: 2026-07-15
---

# M65 项目网点关系与 NETWORK SLA 队列

## 1. 目标

M65 补齐 M64 留下的 NETWORK 项目范围缺口：Project 创建时可以声明零到多个稳定 networkId，项目目录
以带有效期的关系保存；authorization 将有效 NETWORK RoleGrant 解析为精确项目集合，并复用 M63 的
单条范围化 SLA 工作台查询。

本切片不从当前 ServiceAssignment、工单地址、网点名称、区域覆盖或组织关系反推长期项目权限。
ServiceNetwork 目录与生命周期、Coverage/Capability、停派、资质、派单候选和组织层级仍未实现。

## 2. 契约与聚合

- Core OpenAPI 0.36.0 为 `CreateProjectRequest` 和 `Project` 增加可选 `networkIds`；省略或空数组明确
  表示不建立 NETWORK 关系，不存在默认网点。
- 每个引用必须非空、首尾无空白、长度不超过 128；最多 100 个且不得重复。聚合按字典序冻结，保证
  幂等摘要、响应和事件载荷稳定。
- `project.created@v3` 强制携带 `regionCodes` 和 `networkIds`；已发布 v1/v2 保持不变。

## 3. 数据与事务

V065 创建 `prj_project_network`：

- `(tenant_id, project_id)` 复合外键阻止跨租户关系；
- `valid_from/valid_to` 表达关系有效期；
- `(tenant_id, project_id, network_id, valid_from)` 唯一约束阻止重复事实；
- `(tenant_id, network_id, valid_from, valid_to, project_id)` 支持实时范围解析。

Project、REGION/NETWORK 关系、审计、Outbox 和幂等结果在 `project.create` 同一事务提交；非法引用或
任一写入失败时全部回滚。重放从项目目录重建尚未终止的关系，不伪造旧响应。

## 4. 模块边界与授权

`authorization::api` 暴露 `ProjectNetworkScopeResolver`，project 模块实现该端口。authorization 不访问
project 内部包或表；project 继续只依赖 authorization 的公开 API。

授权解析规则：

1. TENANT 表示租户内全部项目；
2. PROJECT UUID、有效 REGION 映射和有效 NETWORK 映射取并集；
3. NETWORK 只精确匹配稳定引用、tenant 和有效期；
4. 没有任何项目时以 `PROJECT_SCOPE_MISSING` 拒绝并审计，不返回租户全量或静默空列表；
5. 最终项目集合摘要绑定 SLA 游标，关系增减使旧游标失败关闭。

## 5. 工程证据

- `ProjectCommandPostgresIT`：项目、两类关系、审计、Outbox v3、幂等原子提交与非法引用回滚；
- `AuthorizationPolicyPostgresIT`：租户隔离、有效期、精确 NETWORK 映射和空映射失败关闭；
- `SlaClockPostgresIT`：NETWORK RoleGrant 进入跨项目 SLA 队列且关系变化使旧游标失效；
- Project domain/MVC、契约兼容、客户端可重复生成和 `ArchitectureTest`；
- PostgreSQL 18 上 67 个迁移到达 V065。

## 6. 明确未实现

- ServiceNetwork 目录、状态/合同、停派、Coverage、Capability、Qualification 与治理 API；
- Project REGION/NETWORK 关系的独立修订、终止和审批命令；
- Organization/Region 目录、层级后代和组织到项目关系；
- 授权缓存、导出、运营分析和 Network Portal；
- BUSINESS 日历、暂停/恢复、预警/升级/通知及其他 SLA subject；
- 网点候选硬过滤、评分、初派和完整自动派单闭环。
