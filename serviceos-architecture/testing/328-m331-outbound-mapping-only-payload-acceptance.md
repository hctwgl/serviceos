---
title: M331 出站提审仅 Mapping 验收矩阵
status: Implemented
milestone: M331
lastUpdated: 2026-07-19
---

# M331 出站提审仅 Mapping 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M331-01 | Bundle 含唯一 OUTBOUND Mapping | 提审创建成功；payload 来自 Mapping；`mappingVersionId=assetVersionId`；审计 APPLIED | `ReviewCasePostgresIT` |
| M331-02 | 无 OUTBOUND Mapping | `VALIDATION_FAILED`；不创建 Delivery | `DefaultOutboundDeliveryService` + 夹具/回归 |
| M331-03 | Profile SPI 无 `buildSubmitPayload` | 编译/单测通过 | `OutboundReviewSubmissionProfilesTest` |
| M331-04 | 模块边界 | ArchitectureTest | ArchitectureTest |

## 明确不验收

- 入站仅 Mapping、defaults/enum/condition DSL、吉利 Sandbox、OpenAPI
