---
title: M244 Technician Portal 联系历史安全摘要验收矩阵
status: Implemented
milestone: M244
lastUpdated: 2026-07-18
---

# M244 Technician Portal 联系历史安全摘要验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M244-01 | 当前责任任务存在联系事实 | 返回渠道、标准结果、开始/结束、下次联系与创建时间 | `TechnicianPortalFeedPostgresIT` |
| M244-02 | 数据库行含对象引用、自由文本、录音引用、actorId | Technician DTO/OpenAPI 不存在这些属性 | PostgreSQL IT + Core OpenAPI 1.0.18 |
| M244-03 | 无联系事实 | `contactAttempts=[]` | 查询端口空集合语义 |
| M244-04 | 其他师傅/网点/撤权任务 | 仍统一 404，不执行可见 fan-in | M243 PostgresIT |
| M244-05 | HTTP 详情响应 | `contactAttempts` 必须存在且为数组 | MVC Security |
| M244-06 | Technician 任务详情 | 展示渠道、结果、时间，不渲染敏感字段 | `technician-portal-task-detail.spec.ts` |
| M244-07 | 模块与迁移 | readmodel 只依赖 Appointment 公开 API；Flyway 100/102 | `ArchitectureTest` + preflight |

## 明确未验收

- 联系对象/电话/地址读取和联系写入；
- 预约、Visit、表单、Evidence、整改写命令；
- 离线工作包、设备命令、同步冲突与通知。
