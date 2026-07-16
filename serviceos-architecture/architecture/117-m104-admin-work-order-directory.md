---
title: M104 Admin 授权工单目录
status: Implemented
milestone: M104
---

# M104 Admin 授权工单目录

## 1. 目标

Admin 只读消费已实现的 `GET /work-orders` 授权目录，支持 status/clientCode 筛选与工作区深链。

## 2. 交付

- 路由 `ADMIN.WORKORDER.LIST`（`/work-orders`）；
- 调用 listAuthorizedWorkOrders；分页 cursor；
- 行内深链到工作区；`npm run build` 通过。

## 3. 明确未实现

SavedView、复杂筛选 AST、命令 UI、OIDC SDK、E2E。
