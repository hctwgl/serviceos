---
title: M403 Admin 主体最近登录读模型
version: 0.1.0
status: Implemented
milestone: M403
lastUpdated: 2026-07-20
---

# M403 Admin 主体最近登录读模型

## 1. 目标

为用户详情「登录与安全」与用户目录「最近登录」列提供正式服务端事实，关闭 UI_DATA_GAP。

## 2. 已实现

| 层 | 内容 |
|---|---|
| Flyway | **V141** `idn_principal_login_event` |
| OpenAPI | **1.0.69** `GET /security-principals/{id}/recent-logins`；目录项 `lastLoginAt` |
| Identity | OIDC `resolveOrRegister` 成功即同事务写入登录事实并裁剪至 50 条 |
| Admin Web | 用户详情最近登录列表；目录最近登录列 |

## 3. 明确未实现

- 失败登录/风控拒绝列表
- IP / User-Agent / 设备指纹
- 组织树任职编辑
- 产品负责人视觉金标

## 4. 权限与隐私

- 列表：`identity.read`
- 不存密码、不存 subject；issuer 仅作渠道展示
- IdentityLink 详情仍需 `identity.readSensitive`
