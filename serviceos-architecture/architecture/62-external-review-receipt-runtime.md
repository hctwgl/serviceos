---
title: M49 ExternalReviewReceipt 最小运行时
version: 0.1.0
status: Implemented
---

# M49 ExternalReviewReceipt 最小运行时

## 1. 实现范围

1. 适配层服务主体通过 `POST /internal/external-review-receipts` 记录不可变 `ExternalReviewReceipt`；
2. 必须关联 `inboundEnvelopeId`、`canonicalMessageId`、`reviewCaseId`、`externalKey`、`callbackBatchRef`、`mappingVersionId`、`result`；
3. 仅允许对 **OPEN** ReviewCase 记录；同事务追加 `ReviewDecision`（`decisionSource=EXTERNAL`）并投影 Case 状态；
4. `REJECTED` 同事务创建客服协调 HUMAN Task（`taskType=evidence.external-coordination`），**不**直接改师傅 Task，**不**自动打开 CorrectionCase；
5. 幂等键：`tenant + inboundEnvelopeId`；能力 `evidence.recordExternalReceipt`；仅 `SERVICE` 主体；
6. OpenAPI **0.24.0**；Flyway **V049**；staging **049/51**。

## 2. 未实现

完整车企 Connector 验签/入站表、CLIENT origin ReviewCase 自动创建、回传批次权威校验、affectedTargets 强校验、二级审批、条件槽位。`WAIVED` 见 M51。
