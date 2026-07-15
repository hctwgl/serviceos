---
title: M62 SLA 授权查询与工作台投影验收矩阵
version: 0.1.0
status: Implemented
---

# M62 SLA 授权查询与工作台投影验收矩阵

| 场景 | 优先级 | 输入/动作 | 预期证据 |
|---|---|---|---|
| M62-AUTH-001 | P0 | 有 `sla.read` 且命中 Project RoleGrant | 工作台、工单和详情查询成功 |
| M62-AUTH-002 | P0 | 缺 capability 或 Project Scope | 403，写拒绝审计，不返回 SLA 数据 |
| M62-AUTH-003 | P0 | 猜测其他 tenant 的实例 ID | 404，不能跨租户读取 |
| M62-AUTH-004 | P0 | 伪造 `X-Tenant-Id` | 忽略 header，只使用 JWT 当前主体 |
| M62-QRY-001 | P0 | 同 deadline 的多个实例分页 | `(deadlineAt,id)` 稳定、无重复遗漏 |
| M62-QRY-002 | P0 | 游标改用其他 status/project/workOrder | 400，游标范围不匹配失败关闭 |
| M62-QRY-003 | P0 | 工单 SLA 列表 | WorkOrder 公开 Scope 端口解析 project 后授权，不跨表访问 |
| M62-QRY-004 | P0 | 读取 SLA 详情 | instance、segment、milestone 历史完整且顺序确定 |
| M62-TIME-001 | P0 | RUNNING 未到期 | 以服务端 asOf 返回 remaining，不接受客户端时间 |
| M62-TIME-002 | P0 | RUNNING 已过期但尚未对账 | 可显示 overdue，但不得伪造 BREACHED 状态 |
| M62-TIME-003 | P0 | BREACHED/MET_LATE/MET | overdue 按权威事实计算；MET 不猜测动态值 |
| M62-CON-001 | P0 | OpenAPI 0.33.0 | 三个 GET 可解析、兼容门禁与 TypeScript 生成可重现 |
| M62-MIG-001 | P0 | PostgreSQL 18 空库迁移 | 64 个迁移到达 v062，查询索引和 capability 存在 |
| M62-MOD-001 | P0 | SLA 查询跨模块协作 | 仅依赖公开 NamedInterface，ArchitectureTest 通过 |

本矩阵不验收 SLA 状态变更命令、BUSINESS 日历、暂停、重算、预警、通知、Portal 前端或考核结算。
