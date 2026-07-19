---
title: M307 NOTIFICATION 运行时
status: Implemented
milestone: M307
lastUpdated: 2026-07-19
relatedMilestones: [M24, M295, M306]
---

# M307 NOTIFICATION 运行时

## 目标

从冻结 Bundle 执行 NOTIFICATION：事件触发匹配、`SERVICEOS_EXPR_V1` when 求值、按角色解析收件人、通道 SPI 发送、幂等与 UNKNOWN 人工接管；缺外部凭据时使用本地参考 Adapter。

## 范围

- `NotificationRuntime.resolveAndDispatch`
- 冻结 Bundle `NOTIFICATION` 资产按 `policyKey` 唯一加载
- 触发器按 `eventType` 匹配；`when` 失败关闭求值
- `recipientRole` → 调用方提供的 `recipientsByRole` 快照
- `NotificationChannelPort` SPI；`LocalReferenceNotificationChannelAdapter`：
  - `IN_APP` / `PUSH` → `SENT`（本地 providerMessageId）
  - `SMS` / `EMAIL`（无凭据）→ `UNKNOWN`（`LOCAL_REFERENCE_NO_CREDENTIALS`），不得伪造成功
- 幂等键 = SHA-256(`tenant|policy|trigger|eventId|recipient|channel`)；重放 → `SENT_REPLAY`
- `requiresManualIntervention`：UNKNOWN / FAILED / 无收件人
- 可审计 explanations + `assetVersionId` / `contentDigest`

## 明确未实现

- 真实短信/邮件供应商 Adapter；模板渲染引擎；RULE / PRICING 运行时
- 事件自动订阅与 Intent/Delivery/Attempt 持久化：见 **M326**

## 验证

```bash
bash scripts/agent-verify.sh test DefaultNotificationRuntimeTest
bash scripts/agent-verify.sh it NotificationRuntimePostgresIT
```
