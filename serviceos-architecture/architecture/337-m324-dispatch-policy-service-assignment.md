---
title: M324 DISPATCH 自动接入 ServiceAssignment
status: Implemented
milestone: M324
lastUpdated: 2026-07-19
relatedMilestones: [M24, M25, M306, M323]
---

# M324 DISPATCH 自动接入 ServiceAssignment

## 目标

HUMAN Task 创建时，若工作流节点冻结了 `dispatchPolicyRef`，则在 `task.created` 可靠消费路径上
用冻结 Bundle 的 DISPATCH 解析 NETWORK 候选，并激活 ACTIVE NETWORK ServiceAssignment（含容量预占确认）。

## 范围与非目标

- 范围：
  - Flyway `V122`：`tsk_task.dispatch_policy_ref`
  - `WorkflowDefinitionParser` 解析 `dispatchPolicyRef` 并贯通创建 Task
  - `ProjectNetworkDirectoryQuery` + `ServiceNetworkDirectoryQuery` 组装候选
  - Inbox `task.dispatch-policy.created.v1`
  - 非空 rank=1 → `activateNetworkFromFrozenDispatchPolicy`（protocol v1：prepare→confirm→activate→complete）
  - 空池 / MANUAL_INTERVENTION / 无容量计数 → 审计 `SERVICE_DISPATCH_POLICY_MANUAL`，不写派单
- 明确不做：
  - TECHNICIAN 自动指派（见 **M332**）
  - ServiceCoverage / 指标快照权威
  - 比例分配闭环、地图 scope
  - 自动 `CapacityAuthorityService.configure` 扩容
  - OpenAPI / Admin 派单工作台

## 设计假设（PARTIAL）

1. 候选 brand/region/business 使用 `*` 通配，直至 ServiceCoverage 落地。
2. 激活深度为 ACTIVE NETWORK（避免仅 PENDING 触发超时 saga）。
3. 缺失 `dsp_capacity_counter` 的网点直接排除，不静默扩容。

## 已实现

- 冻结 `dispatchPolicyRef` 贯通 workflow → task
- 项目/网点目录 + DISPATCH Runtime + 系统激活路径
- PostgreSQL IT：命中强容量网点 / 空容量 MANUAL

## 明确未实现

- RULE/NOTIFICATION/PRICING 业务主链路
- Admin UNKNOWN/Replay 工作台
- 吉利真实 Sandbox

## 验证命令

```bash
bash scripts/agent-verify.sh it DispatchPolicyServiceAssignmentPostgresIT
bash scripts/agent-verify.sh test ArchitectureTest,WorkflowDefinitionParserTest
```
