---
title: M386 工作流设计器验收矩阵
status: Accepted
milestone: M386
lastUpdated: 2026-07-20
---

# M386 工作流设计器验收矩阵

| # | 场景 | 期望 | 证据 |
|---|---|---|---|
| 1 | 打开产品页 | 三栏结构，中文标题 | Playwright |
| 2 | 草稿列表 | 显示 WORKFLOW 草稿中文摘要 | e2e mock |
| 3 | 画布 | WorkflowCanvas 渲染，无 definition-json 主编辑 | e2e |
| 4 | 导航 | Page Registry + 履约中心入口 | CodePageRegistry v19 |
| 5 | 状态 | READY_FOR_REVIEW，非 PRODUCT_ACCEPTED | 文档 |

## 状态

| 维度 | 状态 |
|---|---|
| 技术 | `RUNTIME_CONNECTED` |
| 前端 | `FRONTEND_COMPLETE`（声明范围） |
| 产品 | `READY_FOR_REVIEW` |
| 测试 | `TEST_PASSED` |
| 视觉 | `VISUAL_NOT_REVIEWED` |
| 可访问性 | `A11Y_NOT_REVIEWED` |
