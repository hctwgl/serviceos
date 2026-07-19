---
title: M351 平台终审工作区验收矩阵
status: Implemented
milestone: M351
lastUpdated: 2026-07-19
---

# M351 平台终审工作区验收矩阵

| ID | 级别 | 场景 | 证据 |
|---|---|---|---|
| M351-01 | P0 | 有 workOrder.read 可读取 FINAL_REVIEW `{data,meta}` | `WorkOrderWorkspacePostgresIT#finalReviewSectionReturnsMaskedContactAndOmitsRawPii` |
| M351-02 | P0 | 客户电话/地址已脱敏，响应无完整原文 | 同上 + `WorkOrderMaskedContactTest` |
| M351-03 | P0 | 九项 gateChecks 均返回 | 同上 |
| M351-04 | P0 | 响应无 objectKey / customerMobile 字段 | 同上 + SecurityTest |
| M351-05 | P0 | MVC 安全：未认证 401；契约字段正确 | `WorkOrderWorkspaceControllerSecurityTest#finalReviewWorkspaceSectionContractIsEnforced` |
| M351-06 | P0 | 顶层 workspace 暴露 FINAL_REVIEW availability | `WorkOrderWorkspacePostgresIT#composesWorkspaceWithoutPii...` |
| M351-07 | P1 | Revision 下载授权 operationId 进入 OpenAPI | `serviceos-core-v1.yaml` `authorizeEvidenceRevisionDownload` |
| M351-08 | P1 | CLIENT/无 OPEN INTERNAL 时 DECIDE 禁用 | QueryService `buildAllowedActions` + 空案例 IT |
