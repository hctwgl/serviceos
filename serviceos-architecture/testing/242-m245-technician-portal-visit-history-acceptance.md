---
title: M245 Technician Portal Visit 历史安全摘要验收矩阵
status: Implemented
milestone: M245
lastUpdated: 2026-07-18
---

# M245 Technician Portal Visit 历史安全摘要验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M245-01 | 当前任务有 IN_PROGRESS Visit | 返回序次、状态、到场时间、围栏/策略和版本 | `TechnicianPortalFeedPostgresIT` |
| M245-02 | Visit 行含 GPS/距离/设备/命令/note | DTO/OpenAPI 无这些属性 | PostgreSQL IT + OpenAPI 1.0.19 |
| M245-03 | 无 Visit | `visits=[]` | 查询端口空集合语义 |
| M245-04 | 其他任务/撤权 | 统一 404，不泄露 Visit 存在性 | M243 IT |
| M245-05 | HTTP/页面 | visits 数组展示生命周期且边界提示定位/设备不返回 | MVC + Admin E2E |
| M245-06 | 模块/迁移 | readmodel 仅依赖 fieldwork API；Flyway 100/102 | ArchitectureTest + preflight |

## 明确未验收

Visit 写入、GPS 策略增强、离线命令、表单、Evidence、整改与通知。
