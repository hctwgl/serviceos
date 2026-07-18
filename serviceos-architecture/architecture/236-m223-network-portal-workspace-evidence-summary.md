---
title: M223 Network Portal 工作区 Evidence 槽位/资料项摘要
status: Implemented
milestone: M223
lastUpdated: 2026-07-17
relatedMilestones: [M213, M222, M89, M95, M201, M202]
---

# M223 Network Portal 工作区 Evidence 槽位/资料项摘要

## 目标

在限定工单工作区上接受并交付非 PII 的 `evidenceSlots` / `evidenceItems` 摘要 enrichment。

## 范围与非目标

- 范围：ADR-061；OpenAPI 1.0.3；NETWORK `evidence.read` soft-gate；
  字段复用 Admin `WorkOrderWorkspaceEvidenceSlotSummary` /
  `WorkOrderWorkspaceEvidenceItemSummary`；按 ACTIVE taskIds 过滤；
  未解析槽位任务跳过；Admin Web 展示；catalog 仍 v16；Flyway 仍 100/102；IT/E2E。
- 明确不做：Admin workspace 复用、独立 NP Evidence 列表、缩略图/下载、Revision 图、
  definition JSON、工作台 SLA 风险、notifications、Portal ACK、onBehalf 写控件。

## 已实现

- [x] ADR-061
- [x] OpenAPI 1.0.3 + DTO/编排 + NETWORK 作用域查询端口
- [x] PostgresIT + Security
- [x] Admin Web + E2E

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
