---
title: M110 Admin 项目详情与范围历史
status: Implemented
milestone: M110
---

# M110 Admin 项目详情与范围历史

## 1. 目标

Admin 消费 `GET /projects/{id}` 与范围修订历史查询。

## 2. 交付

- 路由 `ADMIN.PROJECT.DETAIL`（`/projects/:id`）；
- 展示项目当前事实与 scope-revisions 分页；
- 项目目录深链；`npm run build` 通过。

## 3. 明确未实现

项目创建/范围修订命令 UI、配置绑定治理、OIDC SDK、E2E。
