---
title: 履约配置逐路由验收矩阵
version: 0.2.0
status: Baseline
lastUpdated: 2026-07-20
---

# 履约配置逐路由验收矩阵

| 路由 | 页面 | 技术 | 前端 | 产品 | 视觉 | 备注 |
|---|---|---|---|---|---|---|
| `/projects/:id/fulfillment-profiles` | 配置中心 | RUNTIME_CONNECTED | FRONTEND_COMPLETE | READY_FOR_REVIEW | VISUAL_NOT_REVIEWED | M385 切片 A |
| `/projects/:id/fulfillment-profiles/create` | 新建向导 | RUNTIME_CONNECTED | FRONTEND_COMPLETE | READY_FOR_REVIEW | VISUAL_NOT_REVIEWED | M385 |
| `/projects/:id/fulfillment-profiles/:id` | 详情 | RUNTIME_CONNECTED | FRONTEND_COMPLETE | READY_FOR_REVIEW | VISUAL_NOT_REVIEWED | Runbook |
| `/projects/:id/fulfillment-profiles/:id/preview` | 运行说明 | RUNTIME_CONNECTED | FRONTEND_COMPLETE | READY_FOR_REVIEW | VISUAL_NOT_REVIEWED | 去 JSON |
| `/projects/:id/fulfillment-profiles/:id/edit` | 编辑器 | RUNTIME_CONNECTED | FRONTEND_CONNECTED | PRODUCT_REJECTED→部分修正 | VISUAL_NOT_REVIEWED | 仍用 documentJson；M385b |
| `/projects/:id/fulfillment-profiles/:id/publish` | 发布流 | RUNTIME_CONNECTED | FRONTEND_COMPLETE | READY_FOR_REVIEW | VISUAL_NOT_REVIEWED | Runbook+Compare |
