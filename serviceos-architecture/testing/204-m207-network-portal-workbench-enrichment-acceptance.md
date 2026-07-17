---
title: M207 Network Portal 工作台能力门控计数增强验收矩阵
status: Implemented
milestone: M207
lastUpdated: 2026-07-17
---

# M207 Network Portal 工作台能力门控计数增强验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M207-01 | 仅 `networkTask.read` | 基座计数 + unassigned；无 correction/exception/qualification 字段 | pass（NetworkPortalWorkbenchEnrichmentPostgresIT） |
| M207-02 | 另授 `evidence.read` | 含 `openCorrectionCaseCount` 精确值 | pass（NetworkPortalWorkbenchEnrichmentPostgresIT） |
| M207-03 | 另授 `operations.exception.read` | 含 `openOperationalExceptionCount` | pass（NetworkPortalWorkbenchEnrichmentPostgresIT） |
| M207-04 | 另授 `technician.readOwnNetwork` | 含 `pendingQualificationCount` | pass（NetworkPortalWorkbenchEnrichmentPostgresIT） |
| M207-05 | 全能力且计数为 0 | 字段存在且为 0（非省略） | pass（NetworkPortalWorkbenchEnrichmentPostgresIT） |
| M207-06 | 伪造上下文 | 403 PORTAL_CONTEXT_INVALID | pass（IT / E2E） |
| M207-07 | 未认证 | 401 | pass（NetworkPortalControllerSecurityTest） |
| M207-08 | Admin Web 展示计数/capacity/深链 | UI 可见 | pass（network-portal-workbench-enrichment.spec.ts） |
| M207-09 | OpenAPI 0.99.0；Flyway 仍 100/102 | 契约与迁移门禁 | pass（contracts / preflight） |
| M207-10 | ArchitectureTest | 模块边界 | pass（ArchitectureTest） |
