---
title: ADR-080：Network Portal 整改详情残余 Accepted 字段展示
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
  - Evidence Owner
related_adrs:
  - decisions/ADR-047-network-portal-correction-detail.md
  - decisions/ADR-079-network-portal-appointment-contact-history-residual-fields.md
---

# ADR-080：Network Portal 整改详情残余 Accepted 字段展示

## 1. 状态与已接受决策

本 ADR 作为 M242 的边界结论，正式接受：

1. 在 `NETWORK.CORRECTION.QUEUE` 整改详情页做 **UI-only** enrichment：展示 OpenAPI
   `CorrectionCase` 已要求、M209 类型已声明但详情未渲染的非 PII 字段；
2. 详情补齐：`closedBy`、`waivedBy`、`waiveApprovalRef`、`waiveNote`（详情面允许；摘要面仍禁止）；
3. 补传表补齐：`resubmissions[].submittedBy`；
4. **不**在工作区/目录整改摘要上扩展 `waiveNote`/`createdBy`（延续 ADR-063/071）；
5. **不**新增 HTTP/字段、**不**升 OpenAPI（仍 `1.0.16`）、**不**新增 Flyway、**不**新增 pageId；
6. **不**接受：Portal close/waive 写控件、ACK/decide、客户 PII、notifications。

## 2. 上下文

ADR-047 / M209 已接受详情复用完整 `CorrectionCase`；Admin Web 详情仍只渲染 closedAt/waivedAt
时间戳，未展示操作者与豁免引用/备注，补传表亦缺 `submittedBy`。沿用 UI-only 模式即可零契约推进。

## 3. 后果

- Admin Web 整改详情 enrichment + E2E；
- 摘要面与写命令仍须另接受。
