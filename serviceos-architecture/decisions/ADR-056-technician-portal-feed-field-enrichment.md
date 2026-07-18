---
title: ADR-056：Technician Portal Feed/日程/同步摘要 Accepted 字段展示
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Technician Portal Owner
related_adrs:
  - decisions/ADR-033-technician-portal-feed.md
---

# ADR-056：Technician Portal Feed/日程/同步摘要 Accepted 字段展示

## 1. 状态与已接受决策

本 ADR 作为 M218 的边界结论，正式接受：

1. 在 ADR-033 / M195 之上做 **UI-only** enrichment：展示既有 Accepted 非 PII 字段，
   不新增 HTTP 路径或响应字段；
2. `GET /api/v1/technician/me/task-feed`：展示页级 `networkId`/`asOf`；行级补充
   `projectId`/`taskType`/`taskKind`/`stageCode`/`businessType`/`effectiveFrom`；
   支持可选 `sinceCursor`「加载增量」按钮（使用响应 `nextCursor`）；ASSIGNMENT 行
   `taskId` 深链 `/technician-portal/schedule?taskId=`；
3. `GET /api/v1/technician/me/schedule`：展示页级 `networkId`/`asOf`；行级补充
   `workOrderId`/`projectId`/`windowEnd`/`timezone`；水合 `route.query.taskId` 过滤高亮；
4. `GET /api/v1/technician/me/sync-summary`：展示 `networkId`/`asOf`；计数深链
   pending/tombstone → `/technician-portal/task-feed`，appointmentWindow →
   `/technician-portal/schedule`；
5. **不**新增 pageId（catalog 仍 `page-registry-v16`）、**不**升 OpenAPI（仍 `1.0.0`）、
   **不**新增 Flyway；
6. **不**接受：离线工作包 / mobile sync commands、`TECHNICIAN.TASK.DETAIL`、
   `TECHNICIAN.MESSAGE`、GPS/上传、客户 PII、Network Portal SLA/Visit/表单 DTO 发明、
   Admin workspace 复用。

## 2. 上下文

M195 Admin Web 壳仅渲染 Feed/Schedule/Sync 的薄列子集；OpenAPI 与客户端类型已含完整
安全字段。零契约补齐可读性与门户内导航即可持续推进，无需触碰 deferred invent 项。

## 3. 后果

- Admin Web Technician Portal 三页 enrichment + E2E；
- 完整 Technician App / 离线 runtime 仍属后续接受范围。
