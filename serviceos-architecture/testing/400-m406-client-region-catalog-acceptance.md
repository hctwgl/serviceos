---
title: M406 车企/行政区目录验收矩阵
version: 0.1.0
status: Implemented
milestone: M406
lastUpdated: 2026-07-20
---

# M406 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 区域目录搜索 | 青岛返回 CN-3702/370200 及名称 | `ProjectQueryPostgresIT` |
| A2 | 车企登记 | 目录可见显示名 | 同上 |
| A3 | reference-options | 附带目录显示名 | 同上 |
| A4 | 新建项目选择器 | 提示主数据/行政区目录 | Playwright |
| A5 | 模块边界 | ArchitectureTest | ArchitectureTest |

产品状态：`READY_FOR_REVIEW`。
