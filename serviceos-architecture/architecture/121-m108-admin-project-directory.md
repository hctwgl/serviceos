---
title: M108 Admin 授权项目目录
status: Implemented
milestone: M108
---

# M108 Admin 授权项目目录

## 1. 目标

Admin 只读消费已实现的 `GET /projects` 授权项目目录。

## 2. 交付

- 路由 `ADMIN.PROJECT.LIST`（`/projects`）；
- status/clientId 筛选；
- `npm run build` 通过。

## 3. 明确未实现

项目创建/范围修订 UI、配置绑定治理、OIDC SDK、E2E。
