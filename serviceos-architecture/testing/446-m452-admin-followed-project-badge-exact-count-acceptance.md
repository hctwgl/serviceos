---
title: M452 Admin 关注项目角标精确 COUNT 验收矩阵
version: 0.1.0
status: Implemented
milestone: M452
lastUpdated: 2026-07-21
---

# M452 Admin 关注项目角标精确 COUNT 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 具备读能力且无数据 | 角标为 0，全部 `*Truncated=false` | `listEnrichesZeroBadgesWhenReadCapabilitiesPresent` |
| A2 | 缺能力 | 角标字段 null（soft-omit） | 既有 soft-gate IT |
| A3 | Architecture | 模块边界通过 | `ArchitectureTest` |

产品状态：`READY_FOR_REVIEW`。
