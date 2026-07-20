---
title: M366 派单级 supportedClientKinds 过滤（设计提案）
status: Proposed
milestone: M366
lastUpdated: 2026-07-20
relatedMilestones: [M358, M359, M332, M324, M356]
openapiVersion: "1.0.57"
flywayVersion: "133"
---

# M366 派单级 supportedClientKinds 过滤（设计提案）

## 状态

**Proposed**。本文件不是 Implemented 里程碑；在
[`ADR-088`](../decisions/ADR-088-dispatch-supported-client-kinds-filter.md) 被接受
（A1～A5 勾选）前，不得编写派单过滤生产代码，也不得把 `latestMilestone` 推进到 M366。

## 目标（接受后）

在 TECHNICIAN 指派时刻按冻结 Bundle 的 FORM/EVIDENCE `supportedClientKinds` 硬过滤候选，
避免“先派错端、再在 Portal 422”。

## 当前缺口（工程事实）

- M358：资产级定向发布 + 运行时目标外拒单；
- M359：Feed/详情头预检；
- 派单运行时（`DefaultDispatchRuntime` / Manual / Network Portal assign）**不读** `supportedClientKinds`；
- 师傅档案无权威 clientKind 声明。

## 设计入口

权威决策包：`decisions/ADR-088-dispatch-supported-client-kinds-filter.md`（Proposed）。

推荐默认（待接受）：A1-R / A2-R / A3-R / A4-R / A5-R。

## 接受后的实现边界（预告，非承诺）

- 范围：师傅声明能力、Bundle 目标求交、自动 TECHNICIAN 池硬过滤、MANUAL 回退原因、审计；
- 非目标：on-behalf、iOS 执行器、clientVersion 下限、跨区回退、删除执行门禁。

## 验证（仅 Accepted 实现阶段）

```bash
bash scripts/agent-verify.sh it <DispatchClientKindFilterPostgresIT>
bash scripts/agent-verify.sh arch
bash scripts/agent-verify.sh docs
```
