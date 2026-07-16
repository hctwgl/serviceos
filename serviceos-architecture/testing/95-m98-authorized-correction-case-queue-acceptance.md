---
title: M98 授权整改案例队列验收矩阵
status: Implemented
milestone: M98
---

# M98 授权整改案例队列验收矩阵

| 编号 | 场景 | 预期 |
|---|---|---|
| M98-01 | projectId + evidence.read + status=IN_PROGRESS | 仅返回项目内活动整改 Case |
| M98-02 | 缺省 projectId | 实时授权项目集合范围化 SQL；无逐行鉴权 |
| M98-03 | status/task/sourceReviewCase 过滤 | 仅接受稳定枚举与精确 UUID；默认 OPEN |
| M98-04 | 游标 | FIFO 稳定分页；范围或筛选变化后 VALIDATION_FAILED |
| M98-05 | 安全字段 | 不含 digest、操作者、豁免/关闭正文或审批引用 |
| M98-06 | 工程门禁 | OpenAPI 0.68.0、V082/84 migrations、PostgreSQL/MVC/Contract/Client/ArchitectureTest、L3 |
