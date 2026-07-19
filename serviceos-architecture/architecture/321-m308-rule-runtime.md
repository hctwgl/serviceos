---
title: M308 RULE 运行时
status: Implemented
milestone: M308
lastUpdated: 2026-07-19
relatedMilestones: [M24, M294, M307]
---

# M308 RULE 运行时

## 目标

从冻结 Bundle 执行 RULE：条件求值、严重级别聚合、可审计解释；只输出决策，不写领域副作用。

## 范围

- `RuleRuntime.resolve`
- 冻结 Bundle `RULE` 资产按 `ruleKey` 唯一加载
- `subjectType` / `stage` 必须与资产声明一致，否则失败关闭
- 评估全部规则的 `when`；命中收集 `severity` / `rejectReasonCode` / `message`
- 决策聚合：`BLOCK` > `REQUIRE_APPROVAL` > `WARN`(`PASS_WITH_WARNINGS`) > `defaultAction`(`PASS`/`REQUIRE_MANUAL`)
- `assetVersionId` + `contentDigest` + explanations

## 明确未实现

- 自动驱动 ReviewCase 状态机；规则动作副作用写入；PRICING 运行时；条件积木 UI

## 验证

```bash
bash scripts/agent-verify.sh test DefaultRuleRuntimeTest
bash scripts/agent-verify.sh it RuleRuntimePostgresIT
```
