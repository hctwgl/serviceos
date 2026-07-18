---
title: ADR-077：Network Portal 工作区 Visit/表单/Evidence Accepted 字段展示
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
  - Fieldwork Owner
  - Forms Owner
  - Evidence Owner
related_adrs:
  - decisions/ADR-060-network-portal-workspace-visit-form-summary.md
  - decisions/ADR-061-network-portal-workspace-evidence-summary.md
  - decisions/ADR-076-network-portal-appointment-contact-history-fields.md
---

# ADR-077：Network Portal 工作区 Visit/表单/Evidence Accepted 字段展示

## 1. 状态与已接受决策

本 ADR 作为 M239 的边界结论，正式接受：

1. 在 `NETWORK.WORKORDER.WORKSPACE` 的 Visit / 表单提交 / Evidence 槽位与资料项摘要上做
   **UI-only** enrichment：展示 OpenAPI 已要求、客户端类型已声明但工作区行未渲染的非 PII 字段；
2. Visit：补齐 `appointmentId`、`technicianId`、`networkId`、check-in/out 时间、
   `resultCode`/`exceptionCode`、`aggregateVersion`（延续禁止 GPS/note/device）；
3. 表单提交：补齐 `projectId`、`formVersionId`、`submittedAt`、`contentDigest`
   （延续禁止 values/submittedBy/definition）；
4. Evidence 槽位：补齐 template/required/min-max/active/transition/disposition/resolvedAt/
   occurrence/project（延续禁止 definition JSON/缩略图/下载）；
5. Evidence 资料项：补齐 `projectId` 与 `latestRevisionNumber`（延续禁止 Revision 图/file）；
6. **不**新增 HTTP/字段、**不**升 OpenAPI（仍 `1.0.16`）、**不**新增 Flyway、**不**新增 pageId；
7. **不**接受：客户 PII、Admin workspace 复用、独立 Visit/表单/Evidence 列表 API、
   notifications、Portal ACK、工作区摘要扩 actor。

## 2. 上下文

M222/M223 已交付工作区 Visit/表单/Evidence 服务端摘要；Admin Web 仅渲染薄子集。
product/03 §6.1 要求工作区展示本网点 Visit、表单提交摘要与资料。沿用 M220/M238
UI-only Accepted 字段展示模式即可零契约推进。

## 3. 后果

- Admin Web 工作区四类摘要 enrichment + E2E；
- 消息页、Portal ACK、PII 与写命令仍须另接受。
