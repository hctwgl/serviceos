---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148：https://github.com/hctwgl/serviceos/pull/148 — **M321** 入站 Mapping 物化（Draft，base=master）
- PR #149：https://github.com/hctwgl/serviceos/pull/149 — **M322** 出站 Mapping（Draft，base=#148）
- PR #150：https://github.com/hctwgl/serviceos/pull/150 — **M323** ASSIGNEE→TaskAssignment（Draft，base=#149）
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`（PR #147 已合并）
- latestMilestone：**M323**
- Flyway：**121**；OpenAPI：**1.0.43**

## 本回合完成

### M323 ASSIGNEE_POLICY → TaskAssignment

- Flyway `V121`：`tsk_task.assignee_policy_ref`（节点冻结贯通 CreateWorkflowTaskCommand）
- `authorization::api.RolePrincipalDirectoryQuery`：ACTIVE ALLOW RoleGrant → roleCode 主体目录
- `task.created@v1` Inbox 消费者：冻结 Bundle `AssigneePolicyRuntime.resolve`
- 非空候选 → `assignCandidatesFromFrozenPolicy`（`ASSIGNEE_POLICY` / `sourceId=assetVersionId`）
- 空池 / MANUAL_INTERVENTION → 不写候选，Task 保持 READY，审计 MANUAL
- PostgreSQL IT：`AssigneePolicyTaskAssignmentPostgresIT`

### 既有 Draft 栈（未合入）

- M321 入站 Mapping 物化（PR #148）
- M322 出站 Mapping 提审 Payload（PR #149）

## 验证

```text
bash scripts/agent-verify.sh it AssigneePolicyTaskAssignmentPostgresIT
bash scripts/agent-verify.sh test ArchitectureTest,WorkflowDefinitionParserTest
```

均 PASS。未跑全量 `verify-local.sh`（切片级精准验证；合入前可按需 L3）。

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight

## 下一步：M324 DISPATCH → ServiceAssignment

**入口事实源**

- `architecture/319-m306-dispatch-runtime.md`（明确未实现：自动派单落账）
- M323 先例：`task.created` Inbox + 冻结 ref + 系统写路径

**最小 PARTIAL 切片方向**

1. 冻结 DISPATCH 策略 → ServiceAssignment 候选/预占
2. 容量预占与空池 Fallback
3. 不做：比例分配完整闭环、地图 scope、Admin 全量工作台

**合并顺序**：先 #148（M321），再 #149（M322），然后 M323 Draft PR。
