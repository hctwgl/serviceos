---
title: M234 Network Portal 目录页 SLA 风险服务端摘要
status: Implemented
milestone: M234
lastUpdated: 2026-07-17
relatedMilestones: [M221, M224, M233]
---

# M234：Network Portal 目录页 SLA 风险服务端摘要旁载（ADR-072）

## 目标

目录 work-orders/tasks 页可选 `slaRiskSummaries[]`，消除 Admin Web 目录「SLA 风险」列 N+1。

## 范围

- ADR-072；OpenAPI **1.0.14**；NETWORK `sla.read` soft-gate；catalog v16；Flyway 100/102。
- 工单目录按 WO 聚合；任务目录按 task 展开；仅 openCount>0。

## 明确未实现

即将超时窗口、完整 SLA 详情、目录 evidence、notifications、Portal ACK。

## 验证

```bash
bash scripts/verify-local.sh
```
