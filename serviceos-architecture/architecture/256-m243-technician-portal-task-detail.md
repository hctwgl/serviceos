---
title: M243 Technician Portal 当前责任任务在线详情
status: Implemented
milestone: M243
lastUpdated: 2026-07-18
relatedMilestones: [M195, M218, M219]
---

# M243 Technician Portal 当前责任任务在线详情

## 目标

在 M195 Feed 上交付当前师傅 ACTIVE 责任任务的在线、非 PII 详情与门户内深链，为后续在线履约动作提供
稳定入口，同时保持跨网点、其他师傅和撤权任务失败关闭。

## 范围

- 接受 API-06 §11 `GET /technician/me/tasks/{taskId}`；
- `X-Technician-Context` + ACTIVE TechnicianProfile/NetworkTechnicianMembership；
- NETWORK scope `task.readAssigned`；
- ACTIVE TECHNICIAN ServiceAssignment 或同网点 ACTIVE TaskAssignment 双重责任门禁；
- 非 PII Task 头、executionGuarded、resourceVersion 与预约摘要；
- Admin Web `/technician-portal/tasks/:id` 与 Feed 深链；
- Core OpenAPI `1.0.17`；catalog `page-registry-v16`；Flyway 100/102。

## 已实现

- [x] `TechnicianPortalTaskDetail` 机器契约和只读 API；
- [x] 当前主体、网点成员、Capability、ACTIVE 责任四层校验；
- [x] 其他任务统一 404 防枚举；
- [x] 只读事务内组合 Task 权威上下文与非敏感预约摘要；
- [x] Feed → 详情 → 日程深链；
- [x] PostgreSQL IT、MVC Security、Admin Web build/E2E 证据。

## 明确未实现

- 客户 PII、地址、联系人、表单值、资料文件、配置源码；
- Technician 联系/预约、Visit、表单、Evidence、整改写命令；
- 离线工作包、GPS、相机、上传队列和同步冲突；
- MESSAGE/PROFILE 与通知；
- 客户端推导 allowed-actions。

## 事务、授权与失败语义

查询为只读事务，不修改 Task、Assignment 或 Outbox。先校验可信 Technician Portal 上下文和实时
NETWORK Capability，再限定当前主体 ACTIVE 责任；无法证明责任时统一返回 `RESOURCE_NOT_FOUND`，不得
泄露任务是否存在。响应中的状态与版本仅用于展示，后续写命令必须重新鉴权并校验聚合版本。

## 工程证据

- OpenAPI：Core `1.0.17`；
- 后端：`TechnicianPortalFeedPostgresIT`、`TechnicianPortalControllerSecurityTest`；
- 前端：`technician-portal-task-detail.spec.ts`、`npm run build`；
- 模块：`ArchitectureTest`；
- 迁移：无新增，Flyway 100/102。
