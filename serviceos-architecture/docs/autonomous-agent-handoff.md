---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148～#169：M321～M342 Draft stacked
- 本切片：**M343** REFERENCE_OEM SAMPLE Update/Cancel（Draft，base=#169）
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`
- latestMilestone：**M343**
- Flyway：**127**；OpenAPI：**1.0.43**
- `baselineCommit`：功能提交后回填

## 本回合完成

### M340～M343（本会话连续推进）

- M340 FORM ConditionBuilder formValues（#167）
- M341 EVIDENCE requiredWhen（#168）
- M342 嵌套条件组 round-trip（#169）
- M343 REFERENCE_OEM SAMPLE Update/Cancel Mapping（本切片）

## 验证（M343）

```text
bash scripts/agent-verify.sh test UpdateWorkOrderMappingMaterializerTest,CancelWorkOrderMappingMaterializerTest,ArchitectureTest
bash scripts/agent-verify.sh it ReferenceOemInboundUpdateCancelPostgresIT,ReferenceOemInboundOrderPostgresIT
```

## 下一本地主线

1. AMOUNT/加权比例 — **需业务确认口径**（见 dispatch 设计），当前不可可靠实施
2. BUSINESS 日历 SLA / 结算落账 — 大切片，需独立里程碑
3. 吉利联调 — `BLOCKED_EXTERNAL`

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight
