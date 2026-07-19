---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148：https://github.com/hctwgl/serviceos/pull/148 — **M321** 入站 Mapping 物化（Draft，base=master）
- PR #149：https://github.com/hctwgl/serviceos/pull/149 — **M322** 出站 Mapping（Draft，base=#148）
- PR #150：https://github.com/hctwgl/serviceos/pull/150 — **M323** ASSIGNEE→TaskAssignment（Draft，base=#149）
- **M324** DISPATCH→ServiceAssignment Draft PR pending（base=#150）
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`（PR #147 已合并）
- latestMilestone：**M324**
- Flyway：**122**；OpenAPI：**1.0.43**

## 本回合完成

### M324 DISPATCH → ServiceAssignment

- Flyway `V122`：`tsk_task.dispatch_policy_ref`（节点冻结贯通 CreateWorkflowTaskCommand）
- `project::api.ProjectNetworkDirectoryQuery` + `network::api.ServiceNetworkDirectoryQuery`：项目/网点候选目录
- `task.created@v1` Inbox 消费者：冻结 Bundle `DispatchRuntime.resolve`
- 非空 rank=1 → `activateNetworkFromFrozenDispatchPolicy`（ACTIVE NETWORK + 容量预占确认）
- 空池 / MANUAL_INTERVENTION / 无容量计数 → 不写派单，审计 MANUAL
- PostgreSQL IT：`DispatchPolicyServiceAssignmentPostgresIT`

### 既有 Draft 栈（未合入）

- M321 入站 Mapping 物化（PR #148）
- M322 出站 Mapping 提审 Payload（PR #149）
- M323 ASSIGNEE_POLICY → TaskAssignment（PR #150）

## 验证

```text
bash scripts/agent-verify.sh it DispatchPolicyServiceAssignmentPostgresIT
bash scripts/agent-verify.sh test ArchitectureTest,WorkflowDefinitionParserTest
```

均 PASS。未跑全量 `verify-local.sh`（切片级精准验证；合入前可按需 L3）。

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight

## 下一步：M325 RULE → 业务门禁

**入口事实源**

- `architecture/321-m308-rule-runtime.md`（明确未实现：ReviewCase/业务门禁自动驱动）
- M323/M324 先例：`task.created` Inbox + 冻结 ref + 系统写路径

**最小 PARTIAL 切片方向**

1. 冻结 RULE 策略 → Evidence/Task/Review 真实门禁
2. when 求值结果驱动 BLOCK / REQUIRE_APPROVAL / WARN
3. 不做：完整 Admin 规则工作台、NOTIFICATION/PRICING 落账

**合并顺序**：先 #148（M321），再 #149（M322），再 #150（M323），然后 M324 Draft PR。
