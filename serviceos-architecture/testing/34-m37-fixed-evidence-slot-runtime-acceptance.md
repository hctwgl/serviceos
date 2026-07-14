---
title: M37 固定 EvidenceSlot 运行时验收
version: 0.1.0
status: Implemented
---

# M37 固定 EvidenceSlot 运行时验收

| ID | 优先级 | 验收点 | 自动化证据 |
|---|---|---|---|
| M37-TASK-001 | P0 | 工作流 Task 冻结精确 stageCode，首节点与后续节点语义一致 | Workflow/Task PostgreSQL 回归测试、V037 |
| M37-EVD-001 | P0 | `task.created@v1` 按冻结 Bundle、Digest 和 Stage 解析固定 EvidenceSlot | `EvidenceSlotPostgresIT` |
| M37-EVD-002 | P0 | required/optional 与 min/max 默认值生成稳定槽位和初始状态 | `JsonEvidenceTemplateResolverTest`、`EvidenceSlotPostgresIT` |
| M37-EVD-003 | P0 | 无匹配 Stage 保存权威零槽位解析，未解析查询失败关闭 | `EvidenceSlotPostgresIT`、`EvidenceSlotControllerSecurityTest` |
| M37-FAIL-001 | P0 | `requiredWhen` 未获批时整笔回滚，不产生槽位、Inbox 完成、审计或成功事件 | `JsonEvidenceTemplateResolverTest`、`EvidenceSlotPostgresIT` |
| M37-TX-001 | P0 | Resolution、Slot、审计、Outbox 与 Inbox 完成同事务，重复事件不重复 | `EvidenceSlotPostgresIT` |
| M37-DATA-001 | P0 | 租户外键、唯一约束和触发器阻止解析事实覆盖 | `EvidenceSlotPostgresIT`、V038 |
| M37-SEC-001 | P0 | 匿名拒绝，JWT tenant 与实时 Project Scope 决定读取范围 | `EvidenceSlotControllerSecurityTest`、`EvidenceSlotPostgresIT` |
| M37-API-001 | P1 | OpenAPI 0.12.0 和 JSON 响应保持 EvidenceSlot 对象契约 | `EvidenceSlotControllerSecurityTest`、Contract Validation |
| M37-EVT-001 | P1 | `evidence.slots-resolved@v1` Schema 可治理且不复制敏感资料正文 | Event Schema Governance |
| M37-BOUNDARY-001 | P0 | evidence 不访问 task/workflow/configuration 内部包 | `ArchitectureTest` |
| M37-DEP-001 | P0 | staging 正向迁移至 038/40，旧版本负向门禁及回滚演练通过 | staging rehearsal |

本矩阵仅证明固定 EvidenceSlot 和权威空解析。条件切换、文件上传、Revision、Snapshot、审核与整改仍由
M3 EVD-001～EVD-009 后续里程碑验收。
