---
title: M209 Network Portal 整改详情只读 UI
status: Implemented
milestone: M209
lastUpdated: 2026-07-17
relatedMilestones: [M202, M201, M208]
---

# M209 Network Portal 整改详情只读 UI

## 目标

在 M202 已交付的
`GET /api/v1/network-portal/correction-cases/{correctionCaseId}` 之上，交付只读详情页，
展示完整 `CorrectionCase`（含补传历史），并保持任务代补深链。

## 范围与非目标

- 范围：
  - ADR-047：Admin Web `/network-portal/corrections/:id`；
  - 扩展前端 `NetworkPortalCorrectionDetail` 对齐 OpenAPI `CorrectionCase`；
  - 列表案例 ID 深链；任务代补深链；
  - catalog 仍 `page-registry-v15`；OpenAPI 仍 0.99.0；Flyway 仍 100/102；
  - E2E。
- 明确不做：Portal close/waive、新 HTTP/capability/pageId、通知、产能写。

## 事实源

- ADR-047；ADR-040；API-06 §10；Core OpenAPI `getNetworkPortalCorrectionCase` → `CorrectionCase`

## 设计要点

- 无新后端路径；能力仍为 NETWORK `evidence.read`；
- 详情只读，写路径继续走任务页代补 / 既有 resubmit（M201）。

## 已实现

- [x] ADR-047
- [x] NetworkPortalCorrectionDetailPage + 路由
- [x] 列表深链
- [x] 类型对齐 CorrectionCase
- [x] E2E

## 明确未实现

- Portal close/waive/ACK；Admin 写命令伪装。

## 工程证据

- E2E：`network-portal-correction-detail.spec.ts`
- 后端 get 回归：M202 NetworkPortalCorrection Postgres IT / SecurityTest

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
