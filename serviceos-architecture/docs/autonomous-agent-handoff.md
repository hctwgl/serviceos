---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148：https://github.com/hctwgl/serviceos/pull/148 — **M321** 入站 Mapping 物化（Draft，base=master）
- PR #149：https://github.com/hctwgl/serviceos/pull/149 — **M322** 出站 Mapping（Draft，base=#148）
- PR #150：https://github.com/hctwgl/serviceos/pull/150 — **M323** ASSIGNEE→TaskAssignment（Draft，base=#149）
- PR #151：https://github.com/hctwgl/serviceos/pull/151 — **M324** DISPATCH→ServiceAssignment（Draft，base=#150）
- **M325** RULE→INTERNAL ReviewCase.decide Draft PR pending（base=#151）
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`（PR #147 已合并）
- latestMilestone：**M325**
- Flyway：**123**；OpenAPI：**1.0.43**

## 本回合完成

### M325 RULE → INTERNAL ReviewCase.decide

- Flyway `V123`：`tsk_task.rule_ref`（节点冻结贯通 CreateWorkflowTaskCommand）
- `ReviewRuleGate`：`EVIDENCE_REVIEW` / `INTERNAL` + WorkOrder 表达式上下文；冻结 Bundle `RuleRuntime`
- `DefaultReviewCaseService.decide`：`markDecided` 前调用门禁；BLOCK / REQUIRE_APPROVAL 拒绝 APPROVED
- REJECTED 与 forceApprove 不受阻；拒绝审计独立事务（`ReviewRuleDenyAuditor` REQUIRES_NEW）
- ProblemCode：`REVIEW_RULE_BLOCKED` / `REVIEW_RULE_REQUIRES_APPROVAL`
- PostgreSQL IT：`ReviewRuleGatePostgresIT`

### 既有 Draft 栈（未合入）

- M321 入站 Mapping 物化（PR #148）
- M322 出站 Mapping 提审 Payload（PR #149）
- M323 ASSIGNEE_POLICY → TaskAssignment（PR #150）
- M324 DISPATCH → ServiceAssignment（PR #151）

## 验证

```text
bash scripts/agent-verify.sh it ReviewRuleGatePostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```

均 PASS。未跑全量 `verify-local.sh`（切片级精准验证；合入前可按需 L3）。

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight

## 下一步：NOTIFICATION 可靠投递闭环

**入口事实源**

- `architecture/320-m307-notification-runtime.md`（明确未实现：领域事件自动订阅、Outbox 投递）
- M307 LocalReference Adapter 与幂等/UNKNOWN 人工接管先例

**最小 PARTIAL 切片方向**

1. 领域事件 → NotificationRuntime 自动订阅
2. 可靠 Outbox / Attempt / ACK（或等价）投递闭环
3. 不做：真实短信/邮件供应商、Admin 投递工作台、PRICING 落账

**合并顺序**：先 #148（M321），再 #149（M322），再 #150（M323），再 #151（M324），然后 M325 Draft PR。
