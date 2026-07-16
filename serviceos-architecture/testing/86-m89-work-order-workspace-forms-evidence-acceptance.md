---
title: M89 工单工作区表单与资料区块验收矩阵
status: Implemented
milestone: M89
---

# M89 工单工作区表单与资料区块验收矩阵

| ID | 场景 | 期望 |
|---|---|---|
| M89-01 | 有 form.read 且 Task 绑定 Form | section 返回 forms；无 definitionJson |
| M89-02 | 有 evidence.read 且槽位已解析 | section 返回 evidenceSlots；无 requirement/explanation JSON |
| M89-03 | 仅 workOrder.read | 顶层 FORMS_EVIDENCE=UNAVAILABLE |
| M89-04 | cursor 非空 | 400 VALIDATION_FAILED |
| M89-05 | 工程门禁 | OpenAPI 0.59.0、无新 Flyway、PostgreSQL/MVC/Contract/Client/ArchitectureTest、L3 |
