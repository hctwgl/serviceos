---
title: M99 授权外发交付队列验收矩阵
status: Implemented
milestone: M99
---

# M99 授权外发交付队列验收矩阵

| 编号 | 场景 | 预期 |
|---|---|---|
| M99-01 | projectId + integration.readOutbound | 默认 UNKNOWN，仅返回项目内交付 |
| M99-02 | 缺省 projectId | 实时授权项目集合范围化 SQL；无逐行鉴权 |
| M99-03 | status/messageType/workOrder/review 过滤 | 仅接受稳定枚举与精确 UUID |
| M99-04 | 游标 | FIFO 稳定分页；范围或筛选变化后 VALIDATION_FAILED |
| M99-05 | 安全字段 | 不含 digest、对象引用、操作者、重放审批正文或 attempt 明细 |
| M99-06 | 工程门禁 | OpenAPI 0.69.0、V083/85 migrations、PostgreSQL/MVC/Contract/Client/ArchitectureTest、L3 |
