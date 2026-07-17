---
title: M201 Network Portal 资料代补（onBehalf）验收矩阵
status: Implemented
milestone: M201
lastUpdated: 2026-07-17
---

# M201 Network Portal 资料代补（onBehalf）验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M201-01 | ACTIVE 成员 + `evidence.submitOnBehalf` + OPEN 整改 + onBehalfOf=ACTIVE TECHNICIAN → begin/finalize | CaptureMetadata 含 uploadedBy/onBehalfOf/onBehalfReason；Revision 创建 | pass（NetworkPortalEvidenceOnBehalfPostgresIT） |
| M201-02 | 无未关闭 CorrectionCase | `VALIDATION_FAILED` | pass（NetworkPortalEvidenceOnBehalfPostgresIT） |
| M201-03 | onBehalfOf 非 ACTIVE TECHNICIAN | `VALIDATION_FAILED` | pass（NetworkPortalEvidenceOnBehalfPostgresIT） |
| M201-04 | 跨网点 ACTIVE NETWORK | ACCESS_DENIED / PORTAL_CONTEXT_INVALID | pass（NetworkPortalEvidenceOnBehalfPostgresIT） |
| M201-05 | 伪造 `X-Network-Context` | 403 `PORTAL_CONTEXT_INVALID` | pass（NetworkPortalEvidenceOnBehalfPostgresIT / E2E） |
| M201-06 | 普通 Admin begin 路径客户端带 onBehalfOf | 仍失败关闭 | pass（CaptureMetadataValidatorTest） |
| M201-07 | 未认证 HTTP | 401 | pass（NetworkPortalEvidenceControllerSecurityTest） |
| M201-08 | 成员但缺 `evidence.submitOnBehalf` | 403 `ACCESS_DENIED` | pass（NetworkPortalEvidenceControllerSecurityTest / PostgresIT） |
| M201-09 | Portal correction resubmit | 状态 RESUBMITTED / 幂等 | pass（NetworkPortalEvidenceOnBehalfPostgresIT） |
| M201-10 | Admin Web 代补控件；伪造上下文失败关闭 | UI 可见；伪造拒绝 | pass（network-portal-evidence-on-behalf.spec.ts） |
| M201-11 | OpenAPI Core `0.93.0`；Flyway 099/101 | 契约与迁移门禁通过 | pass（contracts / preflight） |
| M201-12 | ArchitectureTest 模块边界 | evidence 经 api 依赖 dispatch/network | pass（ArchitectureTest） |
