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
- PR #152：https://github.com/hctwgl/serviceos/pull/152 — **M325** RULE→ReviewCase.decide（Draft，base=#151）
- PR #153：https://github.com/hctwgl/serviceos/pull/153 — **M326** NOTIFICATION 可靠投递（Draft，base=#152）
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`（PR #147 已合并）
- latestMilestone：**M326**
- Flyway：**124**；OpenAPI：**1.0.43**

## 本回合完成

### M326 NOTIFICATION 可靠投递闭环

- Flyway `V124`：`cfg_notification_intent` / `cfg_notification_delivery` / `cfg_notification_attempt`
- `TaskNotificationEventHandler`：`task.created` / `task.completed` → `NotificationEventDispatchService`
- Inbox `configuration.notification.task-event.v1` + RoleGrant 收件人 + `NotificationRuntime`
- LocalReference SENT → `acknowledged_at` 本地 ACK；空收件人 → Intent PARTIAL + manual
- 模块边界：Handler 在 task，持久化在 configuration（避免循环依赖）
- PostgreSQL IT：`NotificationReliableDeliveryPostgresIT`

### 既有 Draft 栈（未合入）

- M321～M325（PR #148～#152）

## 验证

```text
bash scripts/agent-verify.sh it NotificationReliableDeliveryPostgresIT
bash scripts/agent-verify.sh test ArchitectureTest,DefaultNotificationRuntimeTest
```

均 PASS。未跑全量 verify-local.sh（切片级精准验证）。

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight

## 下一步：PRICING 履约事实与 CalculationSnapshot

**入口事实源**

- `architecture/322-m309-pricing-runtime.md`（明确未实现：FulfillmentFact / Snapshot 持久化）
- M309 PricingRuntime 先例

**最小 PARTIAL 切片方向**

1. 履约事实提取 → PricingRuntime
2. CalculationSnapshot 持久化（不落账）
3. 不做：对账结算、Admin 计价工作台、真实供应商通知

**合并顺序**：#148 → #149 → #150 → #151 → #152 → #153。
