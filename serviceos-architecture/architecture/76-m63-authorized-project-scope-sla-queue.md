---
title: M63 授权项目集合与跨项目 SLA 队列
version: 0.1.0
status: Implemented
---

# M63 授权项目集合与跨项目 SLA 队列

## 1. 范围

M63 补齐 M62 明确留下的跨项目授权范围缺口：

1. authorization 暴露实时 `AuthorizedProjectScope` 公共端口；
2. TENANT RoleGrant 解析为租户内全部项目，多个 PROJECT RoleGrant 合并为显式集合；
3. `GET /api/v1/sla-instances` 的 `projectId` 改为可选，省略时按授权集合查询；
4. SLA 使用一次范围解析和一条范围化 SQL，不逐行鉴权；
5. 稳定游标绑定授权集合摘要，授权新增、撤销或过期后旧游标失败关闭；
6. OpenAPI 0.34.0、V063 索引、PostgreSQL/MVC/Modulith 证据同步交付。

## 2. 授权语义

- RoleGrant 只按数据库当前时刻的有效期、撤销状态、Role 状态与 capability 实时解析；
- TENANT 范围覆盖当前 tenant 的全部项目；没有 TENANT 时只合并精确 PROJECT UUID；
- REGION/NETWORK 当前缺少权威 project 关系映射，单独出现时返回 403 并写拒绝审计；
- 非法 PROJECT `scope_ref` 是授权数据完整性错误，不能忽略、猜测或扩大权限；
- 显式 `projectId` 仍沿用 M62 的单项目实时授权，详情和工单时间线不改变。

## 3. 查询与游标

SLA Repository 接收 `tenantWide + projectIds`，MyBatis XML 始终包含 tenant 条件；非 tenantWide
且集合为空时显式 `AND FALSE`。项目集合以排序后的 UUID 形成 SHA-256 摘要，TENANT 使用独立摘要。
游标绑定摘要、workOrder、status、deadline 与实例 ID，因此不能在授权变化后继续读取旧页面。

V063 增加 `(tenant_id, deadline_at, sla_instance_id)` 索引，支持 TENANT 范围稳定游标；M62 的
tenant/project 索引继续服务显式项目集合。

## 4. 明确未实现

- REGION/NETWORK/组织关系到项目的权威关系投影；
- 授权范围缓存、导出和运营分析；
- BUSINESS 日历、暂停/恢复、免责/重算、预警/升级/通知；
- Portal 前端、考核与结算读取；
- 其他 SLA subject/start/stop 组合。

## 5. 工程证据

- `AuthorizationPolicyPostgresIT`：PROJECT 合并、TENANT 覆盖、REGION 与缺授权失败关闭及拒绝审计；
- `SlaClockPostgresIT`：授权集合 SQL、游标以及 RoleGrant 变化后的旧游标失效；
- `SlaQueryControllerSecurityTest`：可选 project HTTP 边界和 JWT 主体；
- `ArchitectureTest`：SLA 只依赖 authorization 公开 API；
- `V063__add_tenant_sla_query_index.sql`：65 个迁移到达 v063。
