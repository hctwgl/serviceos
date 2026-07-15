---
title: M68 授权工单目录与详情查询实现
status: Accepted
milestone: M68
---

# M68 授权工单目录与详情查询实现

## 1. 目标

提供 `GET /api/v1/work-orders` 与 `GET /api/v1/work-orders/{workOrderId}`，使管理端能按实时
`workOrder.read` 数据范围读取现有 WorkOrder 权威事实。列表使用稳定游标；详情先按 JWT tenant
隔离查找，再按工单所属 project 授权，跨租户不泄露存在性。

## 2. 可靠性边界

- TENANT/PROJECT/REGION/NETWORK RoleGrant 统一解析为授权 project 集合，列表在一条 SQL 中收敛；
- cursor 绑定授权范围摘要与全部筛选条件，授权或筛选变化后失败关闭；
- 只返回当前已持久化的 `RECEIVED/ACTIVE/FULFILLED` 与冻结配置包引用；
- 本切片不返回客户姓名、手机号、地址或 VIN，避免在敏感读取审计形成前扩大暴露面；
- 不实现阶段、任务、派单、SLA 风险、动作计算、生命周期新命令或 Portal 页面。

## 3. 契约与持久化

Core OpenAPI 升级至 0.39.0。V068 注册 `workOrder.read` 并增加 tenant/project/receivedAt 稳定分页索引；
查询使用 MyBatis XML，命令事务和 WorkOrder 生命周期不变。
