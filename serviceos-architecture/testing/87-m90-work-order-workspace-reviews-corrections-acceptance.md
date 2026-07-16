---
title: M90 工单工作区审核与整改区块验收矩阵
status: Implemented
milestone: M90
---

# M90 工单工作区审核与整改区块验收矩阵

| 编号 | 场景 | 预期 |
|---|---|---|
| M90-01 | 有 evidence.read 且 Task 存在 ReviewCase | section 返回 reviews 与决定摘要，不含 note/approvalRef/decidedBy |
| M90-02 | 有 evidence.read 且 Task 存在 CorrectionCase | section 返回 corrections，不含 waiveNote/approvalRef |
| M90-03 | 仅 workOrder.read | 顶层 REVIEWS_CORRECTIONS=UNAVAILABLE |
| M90-04 | cursor 非空 | 400 VALIDATION_FAILED |
| M90-05 | 工程门禁 | OpenAPI 0.60.0、无新 Flyway、PostgreSQL/MVC/Contract/Client/ArchitectureTest、L3 |
