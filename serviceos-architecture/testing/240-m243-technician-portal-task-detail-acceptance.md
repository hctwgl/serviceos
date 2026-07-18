---
title: M243 Technician Portal 当前责任任务在线详情验收矩阵
status: Implemented
milestone: M243
lastUpdated: 2026-07-18
---

# M243 Technician Portal 当前责任任务在线详情验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M243-01 | 当前师傅读取本人 ACTIVE ServiceAssignment 任务 | 返回非 PII 任务头、版本、guard 与预约摘要 | `TechnicianPortalFeedPostgresIT` |
| M243-02 | 同一合法上下文读取其他师傅或其他网点任务 | 统一 404 `RESOURCE_NOT_FOUND`，不泄露存在性 | `TechnicianPortalFeedPostgresIT` |
| M243-03 | 伪造/失效 Technician 上下文 | 403 `PORTAL_CONTEXT_INVALID` | 既有 PostgresIT + MVC Security |
| M243-04 | 未认证读取详情 | 401 | `TechnicianPortalControllerSecurityTest` |
| M243-05 | HTTP 详情携带 X-Technician-Context | Controller 原样传给实时查询边界 | `TechnicianPortalControllerSecurityTest` |
| M243-06 | Feed 点击任务 | 进入 `/technician-portal/tasks/:id`，详情可深链过滤后的日程 | `technician-portal-task-detail.spec.ts` |
| M243-07 | 敏感字段边界 | 契约无地址、联系人、表单值、资料文件、配置源码 | Core OpenAPI 1.0.17 + 契约测试 |
| M243-08 | 模块与迁移 | readmodel 只依赖公开 API；Flyway 保持 100/102 | `ArchitectureTest` + milestone preflight |

## 明确未验收

- 联系/预约、Visit、表单、Evidence、整改写命令；
- 离线工作包、GPS、相机、上传队列、设备命令；
- MESSAGE/PROFILE、通知和客户 PII。
