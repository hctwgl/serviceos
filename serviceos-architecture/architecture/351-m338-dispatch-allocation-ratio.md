---
title: M338 DISPATCH 签约比例缺口评分
status: Implemented
milestone: M338
lastUpdated: 2026-07-19
relatedMilestones: [M337, M324, M306]
---

# M338 DISPATCH 签约比例缺口评分

## 目标

在合格 NETWORK 候选上计算月度签约比例缺口（`committedShare − actualShare`），
供 `ALLOCATION_RATIO_GAP` 评分因子调节派单；比例只调节合格候选，不覆盖硬过滤。

## 范围与非目标

- 范围：
  - Flyway `V127`：`dsp_network_allocation_target`
  - `NetworkAllocationTargetQuery` / `NetworkAllocationActualQuery`（ORDER_COUNT / MONTH）
  - Consumer 填充 `DispatchCandidate.allocationRatioGap`
  - Runtime：`allocationRatio.enabled` 门禁；仅 `ORDER_COUNT`+`MONTH`；其它口径失败关闭
  - IT：欠配弱网优先于高产能已配强网
- 明确不做：
  - AMOUNT / WEIGHTED_VOLUME 口径
  - `qualityMayOverride` 履约质量覆盖
  - Admin 比例维护 UI / OpenAPI
  - `DispatchPolicyAdjustment` 审批流

## 已实现

- 目标表 + 当月 NETWORK 派单计数；gap 注入候选；启用时参与加权评分
- 单元 + `DispatchPolicyServiceAssignmentPostgresIT` + ArchitectureTest

## 明确未实现

- 金额/加权口径、质量覆盖、比例调整审批、师傅级比例

## 验证命令

```bash
bash scripts/agent-verify.sh test DefaultDispatchRuntimeTest
bash scripts/agent-verify.sh it DispatchPolicyServiceAssignmentPostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```
