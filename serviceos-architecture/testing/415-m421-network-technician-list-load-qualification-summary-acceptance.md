---
title: M421 Network 师傅列表资质与开放任务摘要验收矩阵
version: 0.1.0
status: Implemented
milestone: M421
lastUpdated: 2026-07-21
---

# M421 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 列表开放任务 | ACTIVE 责任已指派数进入 `openTaskCount` | `NetworkPortalReadPostgresIT` |
| A2 | 资质计数与摘要 | APPROVED/PENDING 计数与中文摘要正确 | 同上 |
| A3 | 跨网点隔离 | 他网点师傅/计数不可见 | 同上 |
| A4 | fan-in 同口径 | workspace/directory technician 摘要含相同字段 | 同上 |
| A5 | 模块边界 | ArchitectureTest | ArchitectureTest |
| A6 | Network UI | SummaryStrip + 表格展示任务量/资质摘要 | Playwright |

产品状态：`READY_FOR_REVIEW`（不得宣称 PRODUCT_ACCEPTED / VISUAL_APPROVED）。
