---
title: M236 Network Portal 目录页工单头字段
status: Implemented
milestone: M236
lastUpdated: 2026-07-17
relatedMilestones: [M194, M235]
---

# M236：Network Portal 目录页工单头字段（ADR-074）

为 work-orders/tasks 目录项补齐非 PII 工单头：服务产品 / 区域 / `receivedAt`；
OpenAPI **1.0.16**；catalog v16。

## 明确未实现

用户脱敏 PII、独立 updatedAt、目录 reviews、notifications、Portal ACK。

## 验证

```bash
bash scripts/verify-local.sh
```
