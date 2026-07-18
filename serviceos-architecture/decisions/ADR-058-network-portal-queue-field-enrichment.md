---
title: ADR-058：Network Portal 队列/列表 Accepted 字段展示
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-055-network-portal-directory-technician-fanin.md
  - decisions/ADR-056-technician-portal-feed-field-enrichment.md
---

# ADR-058：Network Portal 队列/列表 Accepted 字段展示

## 1. 状态与已接受决策

本 ADR 作为 M220 的边界结论，正式接受：

1. 在 M202/M203/M205/M206/M194 列表页上做 **UI-only** enrichment：展示 OpenAPI 已要求、
   客户端类型已声明但列表未渲染的非 PII 字段；
2. 门户内深链仅指向既有路由：
   - `correctionTaskId` / `handlingTaskId` → `/network-portal/tasks?taskId=`
   - `workOrderId` → `/network-portal/work-orders/{id}`
3. 异常详情残余：`handlingTaskId` 由纯文本改为任务深链；
4. 任务目录补充既有列 `businessType` / `effectiveFrom`；
5. **不**新增 HTTP/字段、**不**升 OpenAPI、**不**新增 Flyway、**不**新增 pageId；
6. **不**接受：Portal ACK/decide/close/waive、Admin Review 深链、SLA/Visit/表单 DTO、
   notifications、客户 PII。

## 2. 上下文

详情页（M209～M212）与目录师傅 fan-in（M217）已交付；四张协作列表仍为薄列子集。
沿用 M218 的 Accepted 字段展示模式即可零契约推进。

## 3. 后果

- Admin Web 四列表 + 异常详情 + 任务目录 enrichment + E2E；
- 工作区 SLA/Visit/表单与消息页仍须另接受。
