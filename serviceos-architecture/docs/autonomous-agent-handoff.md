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
- **M327** PRICING CalculationSnapshot Draft PR pending（base=#153）
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`（PR #147 已合并）
- latestMilestone：**M327**
- Flyway：**125**；OpenAPI：**1.0.43**

## 本回合完成

### M327 PRICING 履约事实与 CalculationSnapshot

- Flyway `V125`：`cfg_fulfillment_fact` / `cfg_calculation_snapshot`（SHADOW）
- `WorkOrderConfigurationBindingQuery` 公开端口
- `WorkOrderFulfilledPricingHandler`：`workorder.fulfilled` → `PricingCalculationSnapshotService`
- Inbox `configuration.pricing.workorder-fulfilled.v1` + PricingRuntime + 审计
- PostgreSQL IT：`PricingCalculationSnapshotPostgresIT`

### 既有 Draft 栈（未合入）

- M321～M326（PR #148～#153）

## 验证

```text
bash scripts/agent-verify.sh it PricingCalculationSnapshotPostgresIT
bash scripts/agent-verify.sh test ArchitectureTest,DefaultPricingRuntimeTest
```

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight

## 下一步：UNKNOWN / Replay Admin 工作台

**入口事实源**

- M318/M319 人工处置与批量 ReplayRequest 已实现后端
- Admin Portal 尚未承载 UNKNOWN Delivery / Replay 运营工作台

**最小 PARTIAL 切片方向**

1. Admin 页面消费既有 UNKNOWN disposition / batch replay API
2. Capability 门禁与失败关闭
3. 不做：自动改状态、二级审批/MFA、PRICING 落账

**合并顺序**：#148 → … → #153 → M327 Draft PR。
