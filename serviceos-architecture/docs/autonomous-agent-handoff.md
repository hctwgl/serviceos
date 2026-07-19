---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- **进行中 Draft PR**：M358 supportedClientKinds 定向发布（依赖 M356 #183、M357 #184）
- PR：https://github.com/hctwgl/serviceos/pull/185
- 分支：`cursor/m358-supported-client-kinds-6a78`
- latestMilestone（本 PR）：**M358**
- Flyway：**131**；OpenAPI：**1.0.52**
- `baselineCommit`：合并后回填

## 负责人决策（已确认）

业务规则先都不动（AMOUNT/加权、BUSINESS SLA/结算、吉利联调均不推进）。

## 下一

1. iOS 条件执行器对齐后全量硬阻断、Feed/详情头拒单、派单过滤；
2. 8 态视觉基线 / admin-pilot 冒烟加固；
3. 独立审核 HUMAN Task 模板分离；
4. 硬门禁项未确认前不发明推进。
