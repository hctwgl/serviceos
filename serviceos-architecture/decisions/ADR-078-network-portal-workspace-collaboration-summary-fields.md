---
title: ADR-078：Network Portal 工作区协作摘要 Accepted 字段展示
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-077-network-portal-workspace-visit-form-evidence-fields.md
  - decisions/ADR-076-network-portal-appointment-contact-history-fields.md
---

# ADR-078：Network Portal 工作区协作摘要 Accepted 字段展示

## 1. 状态与已接受决策

本 ADR 作为 M240 的边界结论，正式接受：

1. 在 `NETWORK.WORKORDER.WORKSPACE` 的预约 / 联系 / 整改 / 审核 / 异常 / 师傅摘要上做
   **UI-only** enrichment：展示 OpenAPI 已要求、客户端类型已声明但行内仍薄渲染的非 PII 字段；
2. 预约：补齐 `assignedNetworkId`、`technicianId`、`aggregateVersion`、`createdAt`；
3. 联系：补齐 `projectId`、`workOrderId`、`startedAt`、`endedAt`、`nextContactAt`；
4. 整改：补齐 project/sourceDecision/correctionTask（深链）/时间/snapshot，以及最新
   resubmission 摘要行；
5. 审核：补齐 project/scope/policy/snapshot/时间/外部引用/reopen，以及最新 decision 摘要行
   （仍禁止 note/approvalRef/decidedBy/createdBy）；
6. 异常：补齐 taxonomy/workOrder/handlingTask（深链）/occurrences/时间/resolution；
7. 师傅：补齐 `principalId`、`profileStatus`、`validFrom`/`validTo`、`membershipVersion`；
8. 附带：任务页联系历史可展示既有 `startedAt`/`endedAt`/`nextContactAt`（仍禁止 party/note/recording）；
9. **不**新增 HTTP/字段、**不**升 OpenAPI（仍 `1.0.16`）、**不**新增 Flyway、**不**新增 pageId；
10. **不**接受：摘要扩 actor/createdBy、客户 PII、Portal ACK/decide、notifications、
    Admin workspace 复用。

## 2. 上下文

M225～M229 / M227～M228 已交付工作区协作服务端摘要；Admin Web 行内仍为薄子集。
product/03 §6.1 要求工作区展示预约、整改、异常与师傅等协作面。沿用 M239 UI-only 模式即可
零契约推进。

## 3. 后果

- Admin Web 工作区协作摘要 enrichment + E2E；
- 消息页、Portal ACK、PII 与写命令仍须另接受。
