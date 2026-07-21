---
title: M448 Admin 工单目录服务端关键词检索验收矩阵
version: 0.1.0
status: Implemented
milestone: M448
lastUpdated: 2026-07-21
---

# M448 Admin 工单目录服务端关键词检索验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | q=外部单号子串 | 命中且 totalCount 对齐 | `listFiltersByKeywordQ` |
| A2 | q=手机后四位 | 仅命中对应工单 | 同上 |
| A3 | q=客户名/地址子串 | 命中 | 同上 |
| A4 | 完整手机号 | IllegalArgumentException | 同上 |
| A5 | q 过短（非后四位） | IllegalArgumentException | 同上 |
| A6 | Admin 关键词控件 | 可见并可触发服务端检索 | Playwright |

产品状态：`READY_FOR_REVIEW`。
