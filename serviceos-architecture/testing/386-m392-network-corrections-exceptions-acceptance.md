---
title: M392 Network 整改与异常中心验收矩阵
status: Accepted
milestone: M392
lastUpdated: 2026-07-20
---

# M392 Network 整改与异常中心验收矩阵

| # | 场景 | 期望 | 证据 |
|---|---|---|---|
| 1 | 整改 SummaryStrip | 待处理计数可见 | Playwright |
| 2 | 代补入口 | 深链任务页 | Playwright + M220 |
| 3 | 异常建议动作 | DISPATCH→分配师傅 | Playwright |
| 4 | 无空 ACK | 页面无标记已处理 | 实现审查 |
| 5 | 字段兼容 | projectId/DISPATCH 等 | M220 e2e |

## 状态

| 维度 | 状态 |
|---|---|
| 技术 | `RUNTIME_CONNECTED` |
| 前端 | `FRONTEND_COMPLETE`（声明范围） |
| 产品 | `READY_FOR_REVIEW` |
| 测试 | `TEST_PASSED` |
| 视觉 | `VISUAL_NOT_REVIEWED` |
| 可访问性 | `A11Y_NOT_REVIEWED` |
