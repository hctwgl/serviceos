---
title: M41 EvidenceSetSnapshot Task 完成门禁验收
version: 0.1.0
status: Implemented
---

# M41 EvidenceSetSnapshot Task 完成门禁验收

| ID | 优先级 | 验收点 | 自动化证据 |
|---|---|---|---|
| M41-EVD-001 | P0 | 资料 Task 仅接受精确 Snapshot 引用与 contentDigest | `EvidenceSetSnapshotPostgresIT` |
| M41-EVD-002 | P0 | 错误摘要 / 跨 Task / 伪造引用拒绝且无完成污染 | `EvidenceSetSnapshotPostgresIT` |
| M41-EVD-003 | P0 | formRef 非空时跳过资料门禁（表单仍由 M35） | 单元/集成覆盖说明 |
| M41-TX-001 | P0 | 校验失败不写 Task/Outbox/幂等成功记录 | PostgreSQL IT |
| M41-API-001 | P1 | OpenAPI 0.16.0 描述资料完成引用 | Contract Validation |
| M41-MOD-001 | P0 | Modulith：evidence 经 task::api 扩展，无反向依赖 | `ArchitectureTest` |

本矩阵仅证明资料完成门禁最小闭环。双引用完成、invalidate 与审核仍属后续里程碑。
