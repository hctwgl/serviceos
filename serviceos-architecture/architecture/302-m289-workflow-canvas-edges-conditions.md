---
title: M289 Workflow 画布建边与网关条件编辑
status: Implemented
milestone: M289
lastUpdated: 2026-07-18
relatedMilestones: [M287, M268]
---

# M289 Workflow 画布建边与网关条件编辑

## 已实现

1. 连接模式：选源节点 → 目标节点，写入 `transitions`；
2. 选中边：编辑 EXCLUSIVE_GATEWAY 出边 `condition`（SERVICEOS_EXPR_V1）；
3. 删除边；虚线表示有条件；
4. Playwright 冒烟；Admin 生产构建通过。

## 明确未实现

并行网关向导、条件积木、自动避障、多 CANARY 自动晋级。
