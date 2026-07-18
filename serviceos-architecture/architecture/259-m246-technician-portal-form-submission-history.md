---
title: M246 Technician Portal 表单提交安全摘要
status: Implemented
milestone: M246
lastUpdated: 2026-07-18
relatedMilestones: [M35, M155, M222, M243, M245]
---

# M246 Technician Portal 表单提交安全摘要

## 范围与证据

- M243 详情可选 `formSubmissions`；
- project scope `form.read` 软门禁；缺权为 null，其余详情不降级失败；
- 复用 `FormSubmissionQueryService` 安全摘要，不读取 values/校验消息/提交人；
- Admin Web 权限提示与摘要表；Core OpenAPI `1.0.20`；Flyway 100/102；catalog v16；
- `TechnicianPortalFeedPostgresIT`、MVC、ArchitectureTest、Admin build/E2E、契约门禁。

## 明确未实现

表单 definition/schema、values、校验消息、草稿/提交/更正写命令；Evidence、整改、离线与通知。

## 授权语义

当前责任与 `task.readAssigned` 先通过，再实时评估 `form.read`。缺权不会调用 Forms 查询；有权时 Forms 服务
再次执行同一 project capability，防止 readmodel 旁路领域授权。
