---
title: M401 Admin 关注项目读模型
version: 0.1.0
status: Implemented
milestone: M401
lastUpdated: 2026-07-20
---

# M401 Admin 关注项目读模型

## 1. 目标

运营工作台母版底部「关注项目」需要正式服务端读模型：个人可关注/取消关注项目，列表按实时 `project.read` 重新鉴权。

## 2. 已实现

| 层 | 内容 |
|---|---|
| Flyway | `V139__create_admin_followed_project.sql` → `rdm_followed_project` |
| OpenAPI | Core **1.0.67**：`/me/followed-projects` GET/PUT/DELETE + status |
| Backend | follow 先鉴权再 upsert；list 失权清理；unfollow 幂等 |
| Admin Web | 工作台「关注项目」区；项目详情「关注/取消关注」 |

## 3. 明确未实现

- 关注项目上的待办/SLA 聚合角标
- 跨 Portal 关注（仅 ADMIN）
- 产品负责人视觉金标

## 4. 权限

- 不新增 capability；follow 依赖 `project.read`
- tenant/principal 仅来自 JWT；禁止跨主体读写
