---
title: M323 ASSIGNEE_POLICY 自动接入 TaskAssignment 验收矩阵
status: Implemented
milestone: M323
lastUpdated: 2026-07-19
---

# M323 ASSIGNEE_POLICY 自动接入 TaskAssignment 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M323-01 | USER_TASK 节点含 assigneePolicyRef | Task 冻结 `assignee_policy_ref` | `AssigneePolicyTaskAssignmentPostgresIT` |
| M323-02 | 冻结策略 + RoleGrant 角色池非空 | CANDIDATE 快照；source=ASSIGNEE_POLICY；sourceId=policy assetVersionId | 同上 |
| M323-03 | 角色池为空 / MANUAL_INTERVENTION | 不写候选；Task READY；审计 MANUAL | 同上 |
| M323-04 | Inbox 幂等 | `task.assignee-policy.created.v1` SUCCEEDED | 同上 |
| M323-05 | 审计 APPLIED | `TASK_ASSIGNEE_POLICY_APPLIED` | 同上 |
| M323-06 | 模块边界 | task → authorization::api / configuration::api | `ArchitectureTest` |
| M323-07 | 工作流解析 | `assigneePolicyRef` 可选文本 | `WorkflowDefinitionParserTest` |

## 明确不验收

- ORGANIZATION/NETWORK 实时成员
- auto-claim
- DISPATCH / ServiceAssignment
- OpenAPI / 新 HTTP
