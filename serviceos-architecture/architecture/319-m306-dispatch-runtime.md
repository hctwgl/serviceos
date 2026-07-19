---
title: M306 DISPATCH 运行时
status: Implemented
milestone: M306
lastUpdated: 2026-07-19
relatedMilestones: [M24, M295, M305]
---

# M306 DISPATCH 运行时

## 目标

从冻结 Bundle 执行 DISPATCH：硬过滤、加权评分、并列处理、无候选降级与可审计解释；确定性，无 ML。

## 范围

- `DispatchRuntime.resolve`
- 结构化 hardFilters（ENABLED/CAPACITY/BRAND_SCOPE/…）；CUSTOM 为工单级表达式闸门
- 结构化 scoring factors × weight；同分 candidateId 字典序
- Fallback onNoCandidate；capacity.reservationRequired 门禁
- 版本锁定 assetVersionId + contentDigest

## 明确未实现

- 比例分配闭环；实时容量权威写入；NOTIFICATION 运行时
- 自动创建 ServiceAssignment：已由 **M324** 在 `task.created` 路径交付（本切片仍只保证 Runtime）

## 验证

```bash
bash scripts/agent-verify.sh test DefaultDispatchRuntimeTest
bash scripts/agent-verify.sh it DispatchRuntimePostgresIT
```
