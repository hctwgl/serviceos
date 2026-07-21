---
title: M424 Network 工单工作区脱敏客户联系摘要验收矩阵
version: 0.1.0
status: Implemented
milestone: M424
lastUpdated: 2026-07-21
---

# M424 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 顶层脱敏字段 | workspace 含 masked* 且与脱敏算法一致 | `NetworkPortalReadPostgresIT` |
| A2 | 无原文 PII | 响应不含完整手机号/完整地址 | 同上 + SecurityTest |
| A3 | 跨网点失败关闭 | 非本网点 ACTIVE 工单 ACCESS_DENIED | `NetworkPortalReadPostgresIT` |
| A4 | 模块边界 | 不复用 Admin WorkOrderWorkspace；ArchitectureTest | ArchitectureTest |
| A5 | Network UI | object-head 展示脱敏客户/手机/地址 | Playwright |

产品状态：`READY_FOR_REVIEW`。
