---
title: M111 Admin 运营异常确认命令
status: Implemented
milestone: M111
---

# M111 Admin 运营异常确认命令

## 1. 目标

在异常队列按服务端 `allowedActions` 执行 `ACKNOWLEDGE`，携带 Idempotency-Key 与 If-Match。

## 2. 交付

- OPEN 且允许 ACKNOWLEDGE 的异常显示确认按钮；
- 可选 note；成功后刷新列表；
- `npm run build` 通过。

## 3. 明确未实现

通用 RESOLVED UI、跨域异常目录、OIDC SDK、E2E。
