---
title: M73 工单核心执行时间线投影验收矩阵
status: Implemented
milestone: M73
---

# M73 工单核心执行时间线投影验收矩阵

| 编号 | 场景 | 预期证据 |
|---|---|---|
| M73-01 | WorkOrder/Workflow/Stage/Task 核心事件 | 统一写入同一工单投影，保留来源事件、业务发生时间和接收时间 |
| M73-02 | Task claimed/started/released/cancelled 缺少 workOrderId | 只通过 `task::api` 权威上下文解析，不跨模块读表 |
| M73-03 | 事件重复 | Inbox 重放不新增条目，同 eventId 不同 digest 失败关闭 |
| M73-04 | 乱序到达 | 接收时间可乱序，查询仍按 occurredAt + entryId 稳定排序 |
| M73-05 | 身份错配 | tenant、aggregate/resource、Project 或 WorkOrder 不一致时事务回滚 |
| M73-06 | 信息最小化 | 不保存/返回 payload、PII、自由文本、resultRef、错误正文、签名或凭据 |
| M73-07 | 稳定分页 | cursor 绑定 workOrderId，跨工单或非法 cursor 返回 400 |
| M73-08 | 实时授权 | `workOrder.read` Project Scope 可读；撤权后 403 + 拒绝审计；跨 tenant 404 |
| M73-09 | HTTP 契约 | 401、ETag、correlation ID、asOf/lastProjectedAt/freshnessStatus 和 Schema 通过 MVC/契约测试 |
| M73-10 | 工程门禁 | V071/73、Core OpenAPI 0.44.0、PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest/L3 全部通过 |

本矩阵不验收 Appointment、Visit、Evidence/Review、Delivery、SLA、异常、试算/结算时间线，亦不验收
投影重建作业、Broker checkpoint、搜索、导出和 Portal。
