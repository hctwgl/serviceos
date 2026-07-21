---
title: M427 Network 工单工作区表单资料授权预览验收矩阵
version: 0.1.0
status: Implemented
milestone: M427
lastUpdated: 2026-07-21
---

# M427 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 图片预览 | Network 工作区资料项对 image/* 展示短时授权图片 | Playwright `network-portal-workspace-evidence-preview.spec.ts` |
| A2 | purpose | 授权请求 purpose=`WORKSPACE_EVIDENCE_PREVIEW`；UI 提示可见 | 同上 |
| A3 | 无预览指针 | 缺 `latestRevisionId`/`latestMimeType` 时不发起授权、展示占位 | 既有 M223/M239 stub + 本切片类型字段 |
| A4 | 契约稳定 | OpenAPI 仍 1.0.91；无 Flyway | 契约未改 |

产品状态：`READY_FOR_REVIEW`。
