---
title: M314 吉利取消/更新本地入站
status: Implemented
milestone: M314
lastUpdated: 2026-07-19
relatedMilestones: [M300, M302, M311]
---

# M314 吉利取消/更新本地入站

## 目标

扩展吉利本地 AES 适配器：7.17 关闭/取消与 7.18 用户信息更新 → 通用 Cancel/Update 管道；Sandbox/OpenAPI 签名仍 BLOCKED_EXTERNAL。

## 范围

- `GeelyInboundCancelOrderService` → `POST .../notify_close_order`
- `GeelyInboundUpdateOrderService` → `POST .../notify_update_order_info`
- `GeelyAesInboundSupport` 共用解密
- VIN 缺省时派生稳定占位（领域命令要求 vehicleVin）
- PostgreSQL IT：create → update → close

## 明确未实现 / BLOCKED_EXTERNAL

- OpenAPI 平台统一签名；Sandbox 联调；7.2～7.16/7.19～7.22；提审出站

## 验证

```bash
bash scripts/agent-verify.sh it GeelyInboundCancelUpdatePostgresIT
```
