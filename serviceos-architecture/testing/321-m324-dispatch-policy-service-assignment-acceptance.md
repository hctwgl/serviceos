---
title: M324 DISPATCH 自动接入 ServiceAssignment 验收矩阵
status: Implemented
milestone: M324
lastUpdated: 2026-07-19
---

# M324 DISPATCH 自动接入 ServiceAssignment 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M324-01 | USER_TASK 含 dispatchPolicyRef | Task 冻结 `dispatch_policy_ref` | `DispatchPolicyServiceAssignmentPostgresIT` |
| M324-02 | 两 ACTIVE 网点 + 容量 | 激活 rank=1 NETWORK；reservation CONFIRMED | 同上 |
| M324-03 | 无容量计数 | 不写 ServiceAssignment；审计 MANUAL；Task READY | 同上 |
| M324-04 | Inbox 幂等 | `task.dispatch-policy.created.v1` SUCCEEDED | 同上 |
| M324-05 | 审计 APPLIED | `SERVICE_DISPATCH_POLICY_APPLIED` | 同上 |
| M324-06 | 模块边界 | dispatch → configuration/project/network/workorder/task api | `ArchitectureTest` |

## 明确不验收

- TECHNICIAN 自动指派
- ServiceCoverage / 比例分配
- OpenAPI / Admin 工作台
