---
title: M427 Network 工单工作区表单资料授权预览
version: 0.1.0
status: Implemented
milestone: M427
lastUpdated: 2026-07-21
relatedMilestones: [M223, M239, M391, M424, M426]
openapiVersion: "1.0.91"
---

# M427 Network 工单工作区表单资料授权预览

## 1. 目标

关闭 M426 残留「Network Portal 资料缩略图 UI」：在 Network 限定工单工作区对图片型 `evidenceItems` 发起短时授权预览，复用 OpenAPI **1.0.91** 已交付的 `latestRevisionId` / `latestMimeType`。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | 无 bump（仍 **1.0.91**）；Network `NetworkPortalWorkOrderWorkspace` 的 `evidenceItems` 已透传预览指针 |
| Network Web | 工作区「资料项摘要」图片卡短时授权预览（purpose=`WORKSPACE_EVIDENCE_PREVIEW`） |
| 客户端 | `authorizeEvidenceRevisionDownload`；摘要仍禁止 `fileObjectId`/永久 URL |
| 证据 | Playwright（授权 stub + 预览图断言 + 截图） |

## 3. 权限与边界

- 预览仍走既有 `evidence.read` + `file.download` 下载授权链
- 工作区门禁仍为 ACTIVE NETWORK assignment；不复用 Admin `WorkOrderWorkspace`
- 不内嵌永久 URL；不新建缩略图存储/OCR/扫描管道；无 Flyway

## 4. 明确未实现

- 非图片类型内联预览器
- 目录列表缩略图列
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
