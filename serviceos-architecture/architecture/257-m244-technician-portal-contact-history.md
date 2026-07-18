---
title: M244 Technician Portal 联系历史安全摘要
status: Implemented
milestone: M244
lastUpdated: 2026-07-18
relatedMilestones: [M31, M160, M232, M243]
---

# M244 Technician Portal 联系历史安全摘要

## 目标

在 M243 当前责任任务详情中展示联系历史的最小安全事实，避免直接复用包含联系对象、自由文本、录音引用和
操作者标识的完整 `ContactAttempt`。

## 范围

- `TechnicianPortalTaskDetail.contactAttempts[]`；
- Appointment 拥有表内固定白名单只读投影；
- 渠道、标准结果、发生时间、下次联系时间；
- M243 上下文、Capability、当前 ACTIVE 责任与防枚举语义不变；
- Admin Web 联系历史表格与 E2E；
- Core OpenAPI `1.0.18`；catalog `page-registry-v16`；Flyway 100/102。

## 已实现

- [x] Appointment 公开只读查询端口与安全摘要 DTO；
- [x] SQL 只选择允许字段，不读取敏感列；
- [x] readmodel 在完成 M243 实时责任门禁后 fan-in 当前 taskId；
- [x] Technician 详情展示渠道、结果、开始/结束和下次联系；
- [x] PostgreSQL IT 以真实敏感列证明响应 DTO 不可承载这些字段；
- [x] MVC、契约、前端 build/E2E 与模块边界证据。

## 明确未实现

- 联系对象、电话、地址、自由文本、录音与操作者信息；
- 联系/预约写命令；
- Visit、表单、Evidence、整改与离线工作包；
- MESSAGE/PROFILE 与通知。

## 事务、授权与失败语义

查询为只读事务。先由 M243 边界校验 Technician 上下文、`task.readAssigned` 和当前 ACTIVE 责任，再以
tenantId + 单一 taskId 查询 Appointment 拥有表。SQL 固定字段白名单是首层数据最小化，DTO 和 OpenAPI
`additionalProperties: false` 是第二层边界；无法证明责任时不执行 fan-in，并统一返回 404。

## 工程证据

- OpenAPI：Core `1.0.18`；
- 后端：`TechnicianPortalFeedPostgresIT`、`TechnicianPortalControllerSecurityTest`；
- 前端：`technician-portal-task-detail.spec.ts`、`npm run build`；
- 模块：`ArchitectureTest`；
- 迁移：无新增，Flyway 100/102。
