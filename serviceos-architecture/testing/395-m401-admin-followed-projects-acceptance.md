---
title: M401 Admin 关注项目验收矩阵
version: 0.1.0
status: Implemented
milestone: M401
lastUpdated: 2026-07-20
---

# M401 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | follow 幂等 | 重复 follow 刷新 followedAt | `FollowedProjectPostgresIT` |
| A2 | 列表失权清理 | 幽灵关注行被删除且不返回 | 同上 |
| A3 | unfollow 幂等 | 二次 unfollow 不报错 | 同上 |
| A4 | 工作台展示 | 关注项目区块可见且可取消 | Playwright workbench |
| A5 | 模块边界 | ArchitectureTest | ArchitectureTest |
| A6 | 契约/迁移 | OpenAPI 1.0.67 + V139 | yaml + Flyway |

产品状态：`READY_FOR_REVIEW`。
