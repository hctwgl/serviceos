---
title: M39 Evidence 机器校验验收
version: 0.1.0
status: Implemented
---

# M39 Evidence 机器校验验收

| ID | 优先级 | 验收点 | 自动化证据 |
|---|---|---|---|
| M39-EVD-001 | P0 | 扫描 CLEAN 后调度 `evidence.machine-validation` | `EvidenceMachineValidationPostgresIT` |
| M39-EVD-002 | P0 | FORMAT/SIZE/CAPTURE_POLICY 通过 → VALIDATED | `EvidenceMachineValidationPostgresIT`、单元测试 |
| M39-EVD-003 | P0 | BLOCK 失败 → VALIDATION_FAILED 且不计入槽位数量 | `EvidenceMachineValidationPostgresIT` |
| M39-EVD-004 | P0 | DUPLICATE 冲突按模板 severity 生效 | `EvidenceMachineValidationPostgresIT` |
| M39-EVD-005 | P0 | 未实现 BLOCK 检查失败关闭；WARN 可 SKIPPED | 单元测试 |
| M39-TX-001 | P0 | 校验事实/状态/投影/Outbox/审计同事务；任务可幂等重试 | PostgreSQL IT |
| M39-DATA-001 | P0 | `evd_evidence_validation` append-only | V040 + PostgreSQL IT |
| M39-API-001 | P1 | OpenAPI 0.14.0 暴露 validations | Contract Validation |
| M39-EVT-001 | P1 | `evidence.validation-completed@v1` Schema 可治理 | Event Schema Governance |
| M39-DEP-001 | P0 | staging 正向迁移至 040/42 | staging rehearsal |

本矩阵仅证明机器校验最小闭环。OCR/审核/整改/完成门禁仍属后续里程碑。
