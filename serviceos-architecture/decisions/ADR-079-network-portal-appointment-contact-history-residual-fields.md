---
title: ADR-079：Network Portal 预约/联系历史残余 Accepted 字段展示
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-076-network-portal-appointment-contact-history-fields.md
  - decisions/ADR-078-network-portal-workspace-collaboration-summary-fields.md
---

# ADR-079：Network Portal 预约/联系历史残余 Accepted 字段展示

## 1. 状态与已接受决策

本 ADR 作为 M241 的边界结论，正式接受：

1. 在任务页预约/联系历史列表上继续做 **UI-only** enrichment：补齐 OpenAPI 已要求、
   M238/M240 未闭合的非 PII 残余字段；
2. 预约历史补齐：`projectId`、`workOrderId`、`assignedNetworkId`、`technicianId`、
   `createdAt`、`allowedActions`（只读展示）、`currentRevisionNo`、窗口
   `estimatedDurationMinutes`；
3. 联系历史补齐：`projectId`、`workOrderId`、`createdAt`（时间窗字段延续 M240）；
4. 对齐客户端 TS 类型与 OpenAPI 非 PII 子集；
5. **禁止**渲染 `addressRef`、`note`、`contactedPartyRef`、`recordingRef`；
6. **不**新增 HTTP/字段、**不**升 OpenAPI（仍 `1.0.16`）、**不**新增 Flyway、**不**新增 pageId；
7. **不**接受：摘要扩 actor 发明、客户 PII、Portal ACK、notifications、今日/明日预约计数。

## 2. 上下文

M238 闭合了 product/03 §8「操作者/渠道」；工作区协作摘要（M240）已展示范围/时间字段，
但任务页完整 `Appointment`/`ContactAttempt` 历史仍薄。沿用 UI-only 模式即可零契约推进。

## 3. 后果

- Admin Web 任务页历史 enrichment + E2E；
- 消息页、Portal ACK、PII 与写命令仍须另接受。
