---
title: M387 任务模板中心验收矩阵
status: Accepted
milestone: M387
lastUpdated: 2026-07-20
---

# M387 任务模板中心验收矩阵

| # | 场景 | 期望 | 证据 |
|---|---|---|---|
| 1 | 读模型 | 从 WORKFLOW 投影 SURVEY/INSTALL 等模板 | PostgresIT |
| 2 | 产品页 | 分类/表格/详情/摘要条 | Playwright |
| 3 | 引用关系 | 展示流程中文名与数量 | e2e |
| 4 | 缺口 | gaps 非空且可读 | API + UI |
| 5 | 状态 | READY_FOR_REVIEW | 文档 |

## 状态

| 维度 | 状态 |
|---|---|
| 技术 | `API_AVAILABLE` |
| 前端 | `FRONTEND_COMPLETE`（声明范围） |
| 产品 | `READY_FOR_REVIEW` |
| 测试 | `TEST_PASSED` |
| 视觉 | `VISUAL_NOT_REVIEWED` |
| 可访问性 | `A11Y_NOT_REVIEWED` |
