---
title: M426 Admin 工单工作区表单资料授权预览验收矩阵
version: 0.1.0
status: Implemented
milestone: M426
lastUpdated: 2026-07-21
---

# M426 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 摘要含预览指针 | evidenceItems 含 latestRevisionId/latestMimeType | `WorkOrderWorkspacePostgresIT` |
| A2 | 不泄漏文件引用 | 响应不含 fileObjectId/captureMetadata | 同上 |
| A3 | 图片预览 | Admin 表单资料页签展示短时授权图片 | Playwright |
| A4 | 模块边界 | ArchitectureTest | ArchitectureTest |

产品状态：`READY_FOR_REVIEW`。
