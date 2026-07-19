---
title: M360 终审 8 态视觉基线与 admin-pilot 冒烟验收矩阵
status: Implemented
milestone: M360
lastUpdated: 2026-07-19
---

# M360 终审 8 态视觉基线与 admin-pilot 冒烟验收矩阵

| ID | 级别 | 场景 | 期望 | 证据 |
|---|---|---|---|---|
| M360-VIS-001 | P0 | loading | 骨架可见并截图 | visual.spec `1 loading` |
| M360-VIS-002 | P0 | empty | 「暂无终审数据」 | visual.spec `2 empty` |
| M360-VIS-003 | P0 | error | 失败 detail 可见 | visual.spec `3 error` |
| M360-VIS-004 | P0 | pending | 「提交终审」禁用 | visual.spec `4 pending` |
| M360-VIS-005 | P0 | approved-ready | 「审核通过」可点 | visual.spec `5 approved-ready` |
| M360-VIS-006 | P0 | rejected-ready | 「驳回整改」可点 | visual.spec `6 rejected-ready` |
| M360-VIS-007 | P0 | readonly | 无 DECIDE 原因可见 | visual.spec `7 readonly` |
| M360-VIS-008 | P0 | conflict | 版本冲突 dialog | visual.spec `8 conflict` |
| M360-VIS-009 | P1 | stale-draft | 旧草稿警告条 | visual.spec 附加 |
| M360-VIS-010 | P0 | 既有 Mock 功能回归 | workspace.spec 2/2 | final-review-workspace.spec.ts |
| M360-VIS-011 | P0 | Admin 构建/单元 | build + unit 通过 | npm |
| M360-VIS-012 | P1 | smoke 脚本纳入 visual | verify-admin-smoke 调用三套件 | verify-admin-smoke.sh |

## 明确不在本矩阵

- iOS 条件执行器；派单过滤；REVIEW_TASK 模板分离；OpenAPI/Flyway 变更。
