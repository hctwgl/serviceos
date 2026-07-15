---
title: M49 ExternalReviewReceipt 验收
version: 0.1.0
status: Implemented
---

# M49 ExternalReviewReceipt 验收

| ID | 优先级 | 场景 | 证据 |
|---|---|---|---|
| M49-REC-001 | P0 | OPEN Case 记录通过回执并追加 EXTERNAL 决定 | `ReviewCasePostgresIT` |
| M49-REC-002 | P0 | REJECTED 创建客服协调 Task，不创建 CorrectionCase | `ReviewCasePostgresIT` |
| M49-REC-003 | P0 | inboundEnvelopeId 幂等重放 | `ReviewCasePostgresIT` |
| M49-REC-004 | P0 | USER 主体或缺少能力拒绝 | `ReviewCasePostgresIT` |
| M49-SEC-001 | P0 | 匿名调用 401 | `ExternalReviewReceiptControllerSecurityTest` |

M49 只证明最小回执链路；`affectedTargets` 权威 SnapshotMember 校验见 M54 验收矩阵。
CLIENT ReviewCase 来源、外部提交 lineage 及回执批次/mapping 冻结匹配见 M55 验收矩阵。
