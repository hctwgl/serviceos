---
title: M70 授权任务队列与详情验收矩阵
status: Implemented
milestone: M70
---

# M70 授权任务队列与详情验收矩阵

| 编号 | 场景 | 预期证据 |
|---|---|---|
| M70-01 | TENANT 与 PROJECT/REGION/NETWORK 范围 | PostgreSQL IT 以一条范围化 SQL 返回精确并集 |
| M70-02 | project/kind/status/assignee=me 筛选 | 只返回精确匹配任务，非法值 400 |
| M70-03 | 优先级稳定分页 | priority/nextRunAt/createdAt/taskId 顺序无重复遗漏 |
| M70-04 | 授权或筛选变化后 cursor 重放 | 400 失败关闭，不退化首页 |
| M70-05 | 项目任务详情 | tenant 隔离后 project task.read 鉴权，越权 403 并审计 |
| M70-06 | 无 project 任务详情 | 只接受 tenant-wide task.read |
| M70-07 | 跨 tenant | 404，不泄露任务存在性 |
| M70-08 | 数据最小化与冻结引用 | 列表无敏感载荷；详情只暴露已定义引用和摘要 |
| M70-09 | 工程门禁 | V070/72 migrations、OpenAPI 0.41.0、PostgreSQL/MVC/Contract/Client/ArchitectureTest/L3 通过 |
