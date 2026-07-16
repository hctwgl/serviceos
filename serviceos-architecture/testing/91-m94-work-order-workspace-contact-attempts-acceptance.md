---
title: M94 工单工作区联系尝试摘要验收矩阵
status: Implemented
milestone: M94
---

# M94 工单工作区联系尝试摘要验收矩阵

| 编号 | 场景 | 预期 |
|---|---|---|
| M94-01 | appointment.read + ContactAttempt | contactAttempts 返回安全摘要 |
| M94-02 | 仅 workOrder.read | appointments/contactAttempts 为 null；availability 不伪造 EMPTY |
| M94-03 | appointment.read 但无尝试 | contactAttempts=[] |
| M94-04 | 排序与 limit | startedAt 倒序、ID 稳定 tie-break，独立截断 |
| M94-05 | 敏感字段 | 不含 contactedPartyRef/note/recordingRef/actorId |
| M94-06 | 工程门禁 | OpenAPI 0.64.0、无新 Flyway、PostgreSQL/MVC/Contract/Client/ArchitectureTest、L3 |
