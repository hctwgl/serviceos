---
title: M413 Network 预约日历验收矩阵
version: 0.1.0
status: Implemented
milestone: M413
lastUpdated: 2026-07-21
---

# M413 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 默认范围 | 今天起 14 天，今日预约落入首日桶 | PostgresIT |
| A2 | 跨度超限 | >31 天 VALIDATION_FAILED | PostgresIT |
| A3 | 日历页 UI | 运营日条/明细/pageId 可见 | Playwright |
| A4 | 导航 | NETWORK.APPOINTMENT → /appointments | routeForPage + catalog v21 |
| A5 | 模块边界 | ArchitectureTest | ArchitectureTest |

产品状态：`READY_FOR_REVIEW`。
