---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148：https://github.com/hctwgl/serviceos/pull/148 — **M321** 入站 Mapping 物化（Draft，base=master）
- PR #149：https://github.com/hctwgl/serviceos/pull/149 — **M322** 出站 Mapping（Draft，base=#148）
- 分支 tip：`cursor/m322-outbound-integration-mapping-88d5` @ `b0a64c1c`
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`（PR #147 已合并）
- latestMilestone：**M322**
- Flyway：**120 / 122**；OpenAPI：**1.0.43**

## 本回合完成

### P0 基线收口

- `baselineCommit` / README / status / handoff 对齐 Configuration-Driven Fulfillment Runtime
- Notification（M307）、Pricing（M309）→ `PARTIAL`
- PR #147 标记已合并

### M321 入站 Mapping 物化（PR #148）

- Mapping 命中字段权威写入建单命令；`mappingVersionId`=`assetVersionId`；Canonical 嵌 digest
- BYD HTTP IT：UPPER Transform 证明 Mapping 权威

### M322 出站 Mapping（PR #149）

- OUTBOUND Runtime + 提审创建路径；零 Mapping 兼容 Profile
- 审计 `OUTBOUND_INTEGRATION_MAPPING_APPLIED`

## 验证

```text
# M321
docs / CreateWorkOrderMappingMaterializerTest / BydCpimInboundOrderHttpPostgresIT
IntegrationMappingRuntimePostgresIT / ArchitectureTest / MultiOemParallelCreateSmokePostgresIT
# M322
docs / DefaultIntegrationMappingRuntimeTest / IntegrationMappingRuntimePostgresIT / ArchitectureTest
```

均 PASS。未跑全量 `verify-local.sh`（切片级精准验证；合入前可按需 L3）。

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight

## 下一步：M323 ASSIGNEE_POLICY → TaskAssignment

**入口事实源**

- `architecture/318-m305-assignee-policy-runtime.md`（明确未实现：自动 assignCandidates）
- SLA 先例：`TaskSlaEventHandler` + `tsk_task.sla_ref`

**最小 PARTIAL 切片**

1. Flyway `V121`：`tsk_task.assignee_policy_ref`（镜像 sla_ref）
2. `WorkflowDefinitionParser` 解析 `assigneePolicyRef` 并传入 `CreateWorkflowTaskCommand`
3. `task.created` Inbox 消费者：冻结 Bundle `AssigneePolicyRuntime.resolve`
4. 非空候选 → `TaskAssignmentService.assignCandidates(ASSIGNEE_POLICY, sourceId=assetVersionId)`
5. 空/MANUAL_INTERVENTION → 失败关闭留 READY，供人工 ManualAssign
6. 最小 RoleGrant→`principalsByRoleCode` 反向查询端口（authorization::api）
7. 不做：M23 prepare/activate 改派、ORGANIZATION/NETWORK 实时成员、DISPATCH、auto-claim

**合并顺序**：先 #148，再 #149；然后开 `cursor/m323-assignee-policy-task-assignment-88d5`。
