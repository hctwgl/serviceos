---
title: ADR-075：Network Portal 工作台统计时间展示
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-058-network-portal-queue-field-enrichment.md
---

# ADR-075：Network Portal 工作台统计时间展示

## 1. 状态与已接受决策

本 ADR 作为 M237 的边界结论，正式接受：

1. 在 `NETWORK.WORKBENCH` 上做 **UI-only** enrichment：展示 OpenAPI 已要求、客户端类型已声明
   但工作台未渲染的非 PII 时间字段；
2. 页级 `asOf` 以产品文案 **统计时间** 展示（对齐 product/03 §4「当前在途量必须显示业务类型和统计时间」）；
3. 各 `capacity[]` 行展示既有 `updatedAt`（与 `NETWORK.CAPACITY` 页口径对齐）；
4. **不**新增 HTTP/字段、**不**升 OpenAPI（仍 `1.0.16`）、**不**新增 Flyway、**不**新增 pageId；
5. **不**接受：今日/明日预约计数、签约比例/评分、目录 reviews、客户 PII、notifications、
   Portal ACK/decide、产能申请写、`CapacityAdjustmentRequest`。

## 2. 上下文

M207/M208/M224 已交付工作台基数、产能深链与薄 SLA 风险计数；产能独立页已展示 `asOf` 与行
`updatedAt`，但工作台容量区块仍只渲染占用/上限，未闭合 product/03 对「统计时间」的 MVP 要求。
沿用 M218/M220 UI-only Accepted 字段展示模式即可零契约推进。

## 3. 后果

- Admin Web 工作台页级统计时间 + 容量行更新时间 + E2E；
- 预约日历计数、评分/签约比例、消息页与写命令仍须另接受。
