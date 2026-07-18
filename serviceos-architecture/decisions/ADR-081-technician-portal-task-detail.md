---
title: ADR-081：Technician Portal 当前责任任务在线详情
status: Accepted
date: 2026-07-18
---

# ADR-081：Technician Portal 当前责任任务在线详情

## 背景

M195/M218 已提供当前师傅任务 Feed、日程和同步摘要，但 Feed 的任务只能跳到日程，无法形成稳定的
任务详情入口。完整师傅 App、客户 PII、现场写动作和离线工作包仍需要独立业务与安全决策，不能在本切片
中一并发明。

## 决策

1. 接受 `GET /api/v1/technician/me/tasks/{taskId}`，归属 `readmodel` 只读编排；
2. 继续要求可信 `X-Technician-Context`、ACTIVE TechnicianProfile/NetworkTechnicianMembership 与
   NETWORK scope `task.readAssigned`；
3. 任务还必须属于当前主体的 ACTIVE TECHNICIAN ServiceAssignment 或同网点 ACTIVE TaskAssignment；
   其他师傅、其他网点、已撤权和不存在的任务统一返回 404，避免资源枚举；
4. 响应仅包含非 PII 任务头、执行保护状态、资源版本和 M195 非敏感预约摘要；禁止地址、联系人、
   表单值、资料文件、配置源码和其他网点事实；
5. Admin Web 增加 `/technician-portal/tasks/:id`，Feed 提供门户内深链；详情页不是底部导航入口，
   因此不新增 Page Registry 项，catalog 保持 `page-registry-v16`；
6. Core OpenAPI 升至 `1.0.17`；不新增数据库结构，Flyway 保持 100/102；
7. 本详情不推导可执行动作。后续联系人/预约、Visit、表单、Evidence、整改命令必须继续由各领域
   命令和实时授权独立接受。

## 明确不接受

- 客户 PII、地址、电话、车辆/设备敏感字段；
- Technician 写命令、GPS、相机、大文件上传；
- 离线工作包、设备命令、同步冲突 runtime；
- 从 Task 状态在客户端推导下一动作；
- Notification/MESSAGE/PROFILE。

## 后果

师傅 Portal 获得稳定、失败关闭的在线任务详情入口，为后续逐个接入预约、Visit、表单、Evidence 与整改
命令提供锚点，同时不会把完整 Admin 工作区或敏感字段复制到 Technician 边界。
