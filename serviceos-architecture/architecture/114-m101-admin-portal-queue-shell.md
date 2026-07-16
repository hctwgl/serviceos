---
title: M101 Admin Portal 队列外壳
status: Implemented
milestone: M101
---

# M101 Admin Portal 队列外壳

## 1. 目标

按 ARCH-19（Vue + TypeScript + Vite）建立 `serviceos-admin-web`，交付 Admin 只读队列外壳，
消费 M97～M100 已实现的专项队列 API。本里程碑不是完整 Portal 产品化。

## 2. 交付

- 应用外壳与导航；
- 路由 Page ID：`ADMIN.REVIEW.QUEUE`、`ADMIN.CORRECTION.QUEUE`、
  `ADMIN.INTEGRATION.OUTBOUND`、`ADMIN.EXCEPTION.QUEUE`；
- Bearer JWT 本机保存；请求只附 Authorization / Correlation-Id；
- `npm run build` 类型检查与生产构建通过。

## 3. 明确未实现

设计系统组件库、SavedView、工单工作区全页、命令/allowed-actions 渲染、Network/Technician、
正式 OIDC 登录 SDK、E2E。
