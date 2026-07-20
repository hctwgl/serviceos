---
title: M402 Admin 用户登记与目录组织/角色摘要
version: 0.1.0
status: Implemented
milestone: M402
lastUpdated: 2026-07-20
---

# M402 Admin 用户登记与目录组织/角色摘要

## 1. 目标

关闭用户管理母版中「新建用户禁用」与「组织/角色列待读模型」缺口：

- 正式登记 USER 主体（**不保存密码**）；
- 用户目录展示组织任职摘要与角色摘要（soft-gate）。

## 2. 已实现

| 层 | 内容 |
|---|---|
| Flyway | **V140** 种子 `identity.register` |
| OpenAPI | **1.0.68**：`POST /security-principals`；`GET /admin/user-directory` |
| Identity | `SecurityPrincipalCommandService.register` + Persona 可选 |
| ReadModel | `AdminUserDirectoryQueryService` 聚合组织/角色摘要 |
| Admin Web | 新建用户 DedicatedFlow；目录列接入摘要 |

## 3. 明确未实现

- 最近登录列表读模型
- 组织树任职编辑与完整审计时间线产品化
- 创建向导内直接完成任职/RoleGrant（仍在详情用既有命令）
- 产品负责人视觉金标

## 4. 权限

- 登记：`identity.register`
- 目录主体：`identity.read`
- 组织摘要：`organization.read`（缺权 → null）
- 角色摘要：`authorization.read`（缺权 → null）
