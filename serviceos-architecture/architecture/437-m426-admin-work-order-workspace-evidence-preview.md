---
title: M426 Admin 工单工作区表单资料授权预览
version: 0.1.0
status: Implemented
milestone: M426
lastUpdated: 2026-07-21
relatedMilestones: [M95, M351, M389, M425]
openapiVersion: "1.0.91"
---

# M426 Admin 工单工作区表单资料授权预览

## 1. 目标

关闭 M389/M423/M425 残留 UI_DATA_GAP「表单资料缩略图」：在 `FORMS_EVIDENCE` 摘要中投影最新 revision 指针与 MIME，Admin 工作区对图片类型发起短时授权预览。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.91** `WorkOrderWorkspaceEvidenceItemSummary` 增加 required nullable `latestRevisionId` / `latestMimeType`；下载授权说明补充 `WORKSPACE_EVIDENCE_PREVIEW` |
| Evidence | `listItemSummaries` LATERAL 选出最新 revision id/mime；摘要仍禁止 `fileObjectId`/digest/URL |
| ReadModel | Admin/NP DTO 对齐透传；Network UI 由 **M427** 交付 |
| Admin Web | 表单资料页签图片缩略预览（purpose=`WORKSPACE_EVIDENCE_PREVIEW`） |
| 证据 | `WorkOrderWorkspacePostgresIT` + Playwright |

## 3. 权限与边界

- 预览仍走既有 `evidence.read` + `file.download` 下载授权链
- 不内嵌永久 URL；不新建缩略图存储/OCR/扫描管道
- 无 Flyway（revision 列已存在）

## 4. 明确未实现

- Network Portal 资料缩略图 UI（已由 **M427** 交付）
- 非图片类型内联预览器
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
