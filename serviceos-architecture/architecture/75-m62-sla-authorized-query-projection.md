---
title: M62 SLA 授权查询与工作台投影实现
version: 0.1.0
status: Implemented
---

# M62 SLA 授权查询与工作台投影实现

## 1. 范围

M62 只把 M61 已存在的 Task ELAPSED SLA 事实安全地暴露给 Admin 工作台和工单时间线：

1. `GET /api/v1/sla-instances` 必须显式提供 `projectId`，支持可选状态过滤和稳定游标；
2. `GET /api/v1/work-orders/{workOrderId}/sla-instances` 从 WorkOrder 权威事实解析 Project Scope；
3. `GET /api/v1/sla-instances/{slaInstanceId}` 返回实例、完整 segment 与 milestone 历史；
4. 所有查询要求 `sla.read`，并由 RoleGrant 实时匹配 tenant/project scope；
5. `asOf`、`remainingSeconds` 和 `overdueSeconds` 只按服务端 Clock 计算，客户端时间不参与；
6. OpenAPI 0.33.0、TypeScript 客户端、V062 查询索引和 PostgreSQL/MVC/Modulith 证据同步交付。

本里程碑不改变 SLA 状态机，也不产生业务事件、业务操作审计或可靠消息；授权拒绝仍按安全基线写入独立审计事务。

## 2. 授权与租户边界

- tenant 只来自 OIDC/JWT 当前主体，任何 `X-Tenant-Id` 都不参与查询；
- 工作台列表先对显式 `projectId` 做一次 `sla.read` Project Scope 实时鉴权，再执行单条范围化 SQL；
- 工单列表通过 `workorder::api` 的最小 `WorkOrderScopeQuery` 解析 project，禁止 SLA 模块读取工单内部表；
- 详情先用 `tenantId + slaInstanceId` 隔离读取，再按冻结 `projectId` 实时鉴权；
- 无授权返回 403 并保留拒绝审计，跨 tenant 对象保持 404；
- `allowedActions=[]` 明确表示 M62 没有暂停、恢复、重算或手工改状态命令。

## 3. 投影与游标

列表以不可变的 `(deadline_at, sla_instance_id)` 正序翻页。游标同时绑定 project、workOrder 和 status，
不能跨筛选条件复用。V062 为项目工作台和工单时间线分别建立 tenant/project 前缀索引。

动态字段遵循：

- RUNNING：返回不小于零的 remaining；调度尚未对账但已过期时同时返回 overdue，不擅自改成 BREACHED；
- BREACHED：以服务端 `asOf - deadlineAt` 返回 overdue；
- MET_LATE：以 `completedAt - deadlineAt` 返回固定 overdue；
- MET：不伪造 remaining 或 overdue。

## 4. 模块与数据边界

- `sla::web -> sla::api -> sla::application -> sla::infrastructure`；
- 查询 SQL 由 MyBatis XML 承载，只访问 SLA 自有表；
- WorkOrder Scope 通过 `workorder::api` 公开只读端口取得；
- V062 只增加索引与 `sla.read` capability，不增加兼容列、默认值或双轨模型。

## 5. 明确未实现

- BUSINESS 工作日历、节假日、跨日班次；
- 暂停/恢复、免责、重算、取消；
- 预警、升级、通知、OperationalException 联动与收件人解析；
- 其他 subject/start/stop 组合；
- Portal 前端工程、考核与结算读取；
- 跨项目综合列表、区域/网点关系范围投影与导出。

## 6. 工程证据

- `SlaClockPostgresIT`：Project Scope、稳定游标、服务端动态时间、详情、工单投影、拒绝和租户隔离；
- `SlaQueryControllerSecurityTest`：JWT、显式 project、tenant header 欺骗与输入边界；
- `ContractValidationTest`、`GeneratedClientContractTest`：OpenAPI 0.33.0 与客户端表面；
- `ArchitectureTest`：SLA 只依赖 WorkOrder/Identity/Authorization 公开 API；
- `V062__add_sla_query_indexes.sql`：64 个迁移到达 v062。
