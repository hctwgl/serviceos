---
title: M394 Technician H5 任务详情作业闭环验收矩阵
status: Accepted
milestone: M394
lastUpdated: 2026-07-20
---

# M394 Technician H5 任务详情作业闭环验收矩阵

| # | 场景 | 期望 | 证据 |
|---|---|---|---|
| 1 | 步骤条 | checkin 为当前 | Playwright |
| 2 | 签到入口 | 可见可点 | Playwright |
| 3 | 提交前检查 | checkin 未满足 | Playwright |
| 4 | 底部主操作 | 到场签到 | Playwright |
| 5 | 既有闭环 | M262～M265 | 既有 e2e |

## 状态

| 维度 | 状态 |
|---|---|
| 技术 | `RUNTIME_CONNECTED` |
| 前端 | `FRONTEND_COMPLETE`（声明范围） |
| 产品 | `READY_FOR_REVIEW` |
| 测试 | `TEST_PASSED` |
| 视觉 | `VISUAL_NOT_REVIEWED` |
| 可访问性 | `A11Y_NOT_REVIEWED` |
