---
title: M323 ASSIGNEE_POLICY 自动接入 TaskAssignment
status: Implemented
milestone: M323
lastUpdated: 2026-07-19
relatedMilestones: [M21, M61, M305, M322]
---

# M323 ASSIGNEE_POLICY 自动接入 TaskAssignment

## 目标

HUMAN Task 创建时，若工作流节点冻结了 `assigneePolicyRef`，则在 `task.created` 可靠消费路径上
用冻结 Bundle 的 ASSIGNEE_POLICY 解析候选 USER，并写入 TaskAssignment CANDIDATE 快照；空池或
MANUAL_INTERVENTION 失败关闭，保留 READY 供人工分配。

## 范围与非目标

- 范围：
  - Flyway `V121`：`tsk_task.assignee_policy_ref`
  - `WorkflowDefinitionParser` 解析节点 `assigneePolicyRef` 并传入 `CreateWorkflowTaskCommand`
  - `authorization::api.RolePrincipalDirectoryQuery`：ACTIVE ALLOW RoleGrant → roleCode 主体目录
  - `task.created@v1` Inbox 消费者 `task.assignee-policy.created.v1`
  - 非空候选 → `TaskAssignmentService.assignCandidatesFromFrozenPolicy`（`ASSIGNEE_POLICY` /
    `sourceId=assetVersionId`；系统 actor；无 HTTP）
  - 审计：`TASK_ASSIGNEE_POLICY_APPLIED` / `TASK_ASSIGNEE_POLICY_MANUAL`
- 明确不做：
  - ORGANIZATION/NETWORK 实时成员解析
  - auto-claim / RESPONSIBLE 自动写入
  - M23 prepare/activate 改派语义变更
  - DISPATCH / ServiceAssignment 自动接入
  - OpenAPI 变更

## 设计要点

1. 策略取自 Task 冻结 Bundle，不读“最新配置”。
2. 无 `assigneePolicyRef`：Inbox N/A 完成，不写候选。
3. 有策略但候选为空或 `requiresManualIntervention`：不写候选，Task 保持 READY，审计 MANUAL。
4. RoleGrant 仅 TENANT/PROJECT 有效 ALLOW；同角色 DENY 排除。
5. 与 Evidence/SLA 同为 `task.created` 本地多消费者；各自 Inbox 幂等。

## 已实现

- 冻结 `assigneePolicyRef` 贯通 workflow → task
- RoleGrant 反向主体目录
- Inbox 消费者 + 系统候选写入路径
- PostgreSQL IT：命中候选 / 空池 MANUAL

## 明确未实现

- DISPATCH 自动 ServiceAssignment
- RULE/NOTIFICATION/PRICING 业务主链路接入
- Admin UNKNOWN/Replay 工作台
- 吉利真实 Sandbox

## 验证命令

```bash
bash scripts/agent-verify.sh it AssigneePolicyTaskAssignmentPostgresIT
bash scripts/agent-verify.sh test ArchitectureTest,WorkflowDefinitionParserTest
```
