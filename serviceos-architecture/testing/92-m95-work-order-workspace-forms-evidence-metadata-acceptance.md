---
title: M95 工单工作区表单提交与资料项安全元数据验收矩阵
status: Implemented
milestone: M95
---

# M95 工单工作区表单提交与资料项安全元数据验收矩阵

| 编号 | 场景 | 预期 |
|---|---|---|
| M95-01 | form.read + FormSubmission | 返回提交版本、digest、状态和校验计数 |
| M95-02 | evidence.read + EvidenceItem | 返回 Item 状态、Revision 数量和最新状态 |
| M95-03 | 缺权 | 对应 forms/evidence 子集为 null，工作区不整体 403 |
| M95-04 | 未解析槽位 | 跳过该 Task slots/items，不伪造权威空槽位 |
| M95-05 | 敏感字段 | 不含 values/校验消息/submittedBy/Revision/file/captureMetadata/createdBy |
| M95-06 | 工程门禁 | OpenAPI 0.65.0、无新 Flyway、PostgreSQL/MVC/Contract/Client/ArchitectureTest、L3 |
