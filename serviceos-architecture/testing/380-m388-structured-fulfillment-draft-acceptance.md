---
title: M388 结构化 Draft 验收矩阵
status: Accepted
milestone: M388
lastUpdated: 2026-07-20
---

# M388 结构化 Draft 验收矩阵

| # | 场景 | 期望 | 证据 |
|---|---|---|---|
| 1 | Draft GET | 返回 `document.stages` | PostgresIT / OpenAPI |
| 2 | Draft PUT | 接受 `document`，拒绝依赖产品 JSON 主路径 | DocumentMapper + IT |
| 3 | 编辑器 | 无 `JSON.parse(documentJson)` 产品路径 | unit guard + e2e |
| 4 | 保存后发布 | 结构化草稿仍可校验/编译/发布 | IT + Playwright |
| 5 | 诊断 | documentJson 仅诊断抽屉 | Editor diagnostics |

## 状态

| 维度 | 状态 |
|---|---|
| 技术 | `API_AVAILABLE` |
| 前端 | `FRONTEND_COMPLETE`（编辑器声明范围） |
| 产品 | `READY_FOR_REVIEW` |
| 测试 | `TEST_PASSED` |
| 视觉 | `VISUAL_NOT_REVIEWED` |
| 可访问性 | `A11Y_NOT_REVIEWED` |
