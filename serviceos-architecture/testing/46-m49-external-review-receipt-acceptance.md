---
title: M49 ExternalReviewReceipt 验收
version: 0.1.0
status: Implemented
---

# M49 ExternalReviewReceipt 验收

| ID | 优先级 | 场景 | 证据 |
|---|---|---|---|
| M49-REC-001 | P0 | OPEN Case 记录通过回执并追加 EXTERNAL 决定 | `ExternalReviewReceiptPostgresIT` |
| M49-REC-002 | P0 | REJECTED 创建客服协调 Task，不创建 CorrectionCase | `ExternalReviewReceiptPostgresIT` |
| M49-REC-003 | P0 | inboundEnvelopeId 幂等重放 | `ExternalReviewReceiptPostgresIT` |
| M49-REC-004 | P0 | USER 主体或缺少能力拒绝 | `ExternalReviewReceiptPostgresIT` |
| M49-SEC-001 | P0 | 匿名调用 401 | `ExternalReviewReceiptControllerSecurityTest` |
