---
title: M307 NOTIFICATION 运行时验收矩阵
status: Implemented
milestone: M307
lastUpdated: 2026-07-19
---

# M307 NOTIFICATION 运行时验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| N307-01 | eventType 匹配 + when 真 + IN_APP | SENT；不要求人工接管 | `DefaultNotificationRuntimeTest#matchesTriggerEvaluatesWhenAndSendsInApp` |
| N307-02 | SMS 无凭据 | UNKNOWN + `LOCAL_REFERENCE_NO_CREDENTIALS` + 人工接管 | `DefaultNotificationRuntimeTest#smsWithoutCredentialsIsUnknownAndRequiresManualIntervention` |
| N307-03 | 同幂等键重放 | SENT_REPLAY，providerMessageId 不变 | `DefaultNotificationRuntimeTest#idempotentReplayReturnsSentReplay` |
| N307-04 | 角色无收件人 | 无 attempt + 人工接管 | `DefaultNotificationRuntimeTest#emptyRecipientsRequiresManualIntervention` |
| N307-05 | policyKey 缺失 | RESOURCE_NOT_FOUND 失败关闭 | `DefaultNotificationRuntimeTest#failsClosedWhenPolicyMissing` |
| N307-06 | 冻结 Bundle PostgreSQL | 发布资产后 resolveAndDispatch SENT | `NotificationRuntimePostgresIT#dispatchesFromFrozenNotificationPolicy` |
