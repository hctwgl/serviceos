---
title: M448 Admin 工单目录服务端关键词检索
version: 0.1.0
status: Implemented
milestone: M448
lastUpdated: 2026-07-21
relatedMilestones: [M192, M429, M444, M447]
openapiVersion: "1.0.110"
---

# M448 Admin 工单目录服务端关键词检索

## 1. 目标

关闭 Admin 工单目录母版「关键词」缺口：服务端 `q` 检索工单编号/客户名/手机后四位/地址，并与精确 `totalCount` 对齐。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.110**：`q`（1–200） |
| Backend | 4 位数字 → 手机后四位；其余 ILIKE 编号/姓名/地址；完整手机号失败关闭；filterDigest |
| Admin Web | 默认区关键词传 `q`；移除客户端关键词过滤 |
| 证据 | PostgresIT + MVC + Playwright |

## 3. 边界

- 能力仍为 `workOrder.read`；原始联系仅在 workorder SQL 内匹配，响应仍脱敏
- 不扩展 ControlledSearch；无 Flyway / 无新 capability

## 4. 明确未实现

- `pg_trgm` / 全文索引平台
- SavedView 目录登记 `q`
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
