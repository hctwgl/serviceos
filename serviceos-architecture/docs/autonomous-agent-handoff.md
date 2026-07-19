---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- **进行中 Draft PR**：M356 客户端能力兼容发布门禁
- PR：https://github.com/hctwgl/serviceos/pull/183
- 分支：`cursor/m356-client-capability-compat-gate-6a78`
- latestMilestone（本 PR）：**M356**
- Flyway：**130**；OpenAPI：**1.0.50**
- `baselineCommit`：合并后回填

## 负责人决策（已确认）

业务规则先都不动（AMOUNT/加权、BUSINESS SLA/结算、吉利联调均不推进）。

## 下一

1. M356 后续：灰度/`supportedClientKinds`、任务详情运行时拒单、iOS 条件执行器对齐；
2. 8 态视觉基线 / admin-pilot 冒烟加固；
3. 独立审核 HUMAN Task 模板分离；
4. 硬门禁项未确认前不发明推进。
