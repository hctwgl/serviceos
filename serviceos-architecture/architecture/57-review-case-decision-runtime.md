---
title: M44 ReviewCase / ReviewDecision 最小运行时
version: 0.1.0
status: Implemented
---

# M44 ReviewCase / ReviewDecision 最小运行时

## 1. 批准边界

用户已批准本切片。同步接受：

- ADR-008 中与本切片相关的“审核决定只追加、精确引用资料版本”约束；
- `architecture/10-evidence-review-correction.md` 中 ReviewCase/ReviewDecision 的最小子集。

本切片**不**把整份 architecture/10 标为全面 Implemented。

## 2. 实现范围

1. `ReviewCase` 仅绑定一个 `purpose=TASK_SUBMISSION` 的 `EvidenceSetSnapshot`；
2. 同一 Snapshot 仅允许一个 ReviewCase；
3. 只追加 `ReviewDecision`：`APPROVED` / `REJECTED`；
4. Case 状态投影：`OPEN → APPROVED|REJECTED`；已裁决不可再裁决；
5. Capability：`evidence.review`（创建/裁决），查询复用 `evidence.read`；
6. 同事务：幂等、审计、Outbox、状态更新；
7. OpenAPI **0.19.0**；Flyway **V044**；staging **044/46**。

## 3. API

- `POST /api/v1/review-cases`
- `GET /api/v1/review-cases/{reviewCaseId}`
- `POST /api/v1/review-cases/{reviewCaseId}:decide`

## 4. 事件

- `evidence.review-case-created@v1`
- `evidence.review-decided@v1`

## 5. 未实现

CorrectionCase、强制通过、重开、车企回执、字段级审核、OCR/CV、files 作废联动。
