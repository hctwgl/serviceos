---
title: M425 Admin 工单工作区完整审核决策记录验收矩阵
version: 0.1.0
status: Implemented
milestone: M425
lastUpdated: 2026-07-21
---

# M425 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 决策记录可见 | 审核页签展示 ordinal/decision/source/reasonCodes/decidedAt | Playwright |
| A2 | 补传轮次可见 | 整改卡展示 resubmission ordinal/snapshot/submittedAt | Playwright |
| A3 | 无敏感扩字段 | UI 不渲染 note/decidedBy/approvalRef | 代码审查 + 既有 OpenAPI 省略 |
| A4 | 详情深链保留 | 可打开审核/整改详情 | Playwright + 既有深链 |

产品状态：`READY_FOR_REVIEW`。
