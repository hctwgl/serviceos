---
title: M97 授权审核案例队列验收矩阵
status: Implemented
milestone: M97
---

# M97 授权审核案例队列验收矩阵

| 编号 | 场景 | 预期 |
|---|---|---|
| M97-01 | projectId + evidence.review | 仅返回项目内默认 OPEN Case |
| M97-02 | 缺省 projectId | 实时授权项目集合范围化 SQL；无逐行鉴权 |
| M97-03 | origin/task/status 过滤 | 仅接受稳定枚举与精确 Task |
| M97-04 | 游标 | FIFO 稳定分页；范围或筛选变化后 VALIDATION_FAILED |
| M97-05 | 安全字段 | 不含 snapshot digest、createdBy、决定文本/审批/操作者 |
| M97-06 | 工程门禁 | OpenAPI 0.67.0、V081/83 migrations、PostgreSQL/MVC/Contract/Client/ArchitectureTest、L3 |
