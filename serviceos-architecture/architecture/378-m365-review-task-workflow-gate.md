---
title: M365 REVIEW_TASK 工作流门闸推进（A5-B）
status: Implemented
milestone: M365
lastUpdated: 2026-07-20
relatedMilestones: [M364, M270, M271, M18]
openapiVersion: "1.0.57"
flywayVersion: "133"
---

# M365 REVIEW_TASK 工作流门闸推进（A5-B）

## 状态

**Implemented**。承接已接受 ADR-087 的可选增强 **A5-B**（在 M364 A1-R～A5-R 之上）。

## 目标

APPROVED / FORCE_APPROVED 后推进工作流离开试点模板的 `REVIEW_TASK` 节点，同时保持
M364 的 A2-R（ReviewCase 驱动 `reviewTaskId` handling Task）不被破坏。

## 已实现范围

1. 试点模板 `home-charging-survey-install` **1.1.0**：主路径
   `INSTALL_TASK → REVIEW_TASK → WAIT_OEM_ACK`；
2. 解析器：`REVIEW_TASK` 激活为 **WAITING 门闸**（`serviceos.review.approved` /
   `workOrder:{workOrderId}`），**不** `createWorkflowTask`；
3. `JooqWorkflowReviewDecidedHandler` 消费 `evidence.review-decided`：
   - APPROVED / FORCE_APPROVED → 唤醒门闸或写入早期信号；
   - REJECTED → 忽略（留在门闸，等待整改复审）；
4. Flyway V133：`wfl_review_gate_early_signal`（先审后到闸）；
5. 门闸激活时立即消费未消耗的早期信号。

## 明确未实现

- 把 `reviewTaskId` 改成工作流 HUMAN Task（A2-B）；
- Jump 到 `REVIEW_TASK` 的特殊语义；
- CLIENT ReviewCase 门闸；
- 全量标准模板。

## 验证

```bash
bash scripts/agent-verify.sh test WorkflowDefinitionParserTest
bash scripts/agent-verify.sh it HomeChargingSurveyInstallTemplatePostgresIT
bash scripts/agent-verify.sh arch
bash scripts/agent-verify.sh docs
```
