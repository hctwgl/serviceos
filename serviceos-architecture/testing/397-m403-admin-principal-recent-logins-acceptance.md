---
title: M403 Admin 最近登录验收矩阵
version: 0.1.0
status: Implemented
milestone: M403
lastUpdated: 2026-07-20
---

# M403 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 成功认证 | 写入登录事件，无 subject 列 | `IdentityDirectoryPostgresIT` |
| A2 | 多次登录 | 按时间倒序返回 | 同上 |
| A3 | 用户详情 | 「最近登录」可见 | Playwright |
| A4 | 用户目录 | lastLoginAt 列可见 | Playwright |
| A5 | 模块边界 | ArchitectureTest | ArchitectureTest |

产品状态：`READY_FOR_REVIEW`。
