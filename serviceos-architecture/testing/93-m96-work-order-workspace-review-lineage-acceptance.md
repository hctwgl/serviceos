---
title: M96 工单工作区审核血缘元数据验收矩阵
status: Implemented
milestone: M96
---

# M96 工单工作区审核血缘元数据验收矩阵

| 编号 | 场景 | 预期 |
|---|---|---|
| M96-01 | INTERNAL Case | CLIENT lineage 字段为 null |
| M96-02 | CLIENT Case | source/submission/batch/mapping 血缘完整返回 |
| M96-03 | 重开 successor | reopenedFromReviewCaseId/reopenTriggerRef 返回 |
| M96-04 | 敏感字段 | 不含 snapshot digest、createdBy、决定/豁免文本与操作者 |
| M96-05 | 缺 evidence.read | reviews/corrections 降级为 null，工作区不整体 403 |
| M96-06 | 工程门禁 | OpenAPI 0.66.0、无新 Flyway、PostgreSQL/MVC/Contract/Client/ArchitectureTest、L3 |
