---
title: M309 PRICING 运行时
status: Implemented
milestone: M309
lastUpdated: 2026-07-19
relatedMilestones: [M24, M295, M308]
---

# M309 PRICING 运行时

## 目标

从冻结 Bundle 试算 PRICING：条件匹配计费行、`amountMinor` 合计、币种与可审计解释；不落账、不创建结算单。

## 范围

- `PricingRuntime.resolve`
- 冻结 Bundle `PRICING` 资产按 `pricingKey` 唯一加载
- 逐行 `when`（`SERVICEOS_EXPR_V1`）求值；命中累加 `amountMinor`（`Math.addExact` 溢出失败关闭）
- 输出 `currency`、`matchedLines`（lineKey/chargeCode/amountMinor/billableTo）、`totalAmountMinor`
- `assetVersionId` + `contentDigest` + explanations

## 明确未实现

- 结算落账；对账；动态计价公式；税务；低代码条件积木增强
- 履约事实提取与 CalculationSnapshot 持久化：见 **M327**

## 验证

```bash
bash scripts/agent-verify.sh test DefaultPricingRuntimeTest
bash scripts/agent-verify.sh it PricingRuntimePostgresIT
```
