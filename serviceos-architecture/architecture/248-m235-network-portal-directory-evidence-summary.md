---
title: M235 Network Portal 目录页资料 Evidence 服务端摘要
status: Implemented
milestone: M235
lastUpdated: 2026-07-17
relatedMilestones: [M223, M233, M234]
---

# M235：Network Portal 目录页资料 Evidence 服务端摘要旁载（ADR-073）

闭合 product/03 目录「资料」列：work-orders/tasks 可选 `evidenceSlots`/`evidenceItems`；
NETWORK `evidence.read` soft-gate；复用 M223 装载路径；OpenAPI **1.0.15**；catalog v16。

## 明确未实现

缩略图/下载、Revision 图、definition JSON、独立 NP Evidence API、notifications、Portal ACK、用户脱敏。

## 验证

```bash
bash scripts/verify-local.sh
```
