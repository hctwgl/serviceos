---
title: M355 终审 E2E 验收矩阵
status: Implemented
milestone: M355
lastUpdated: 2026-07-19
---

# M355 终审 E2E 验收矩阵

| ID | 级别 | 场景 | 证据 |
|---|---|---|---|
| M355-01 | P0 | OPEN INTERNAL 工作区 Fan-in | WorkOrderWorkspacePostgresIT / FinalReview query |
| M355-02 | P0 | APPROVED 派生与幂等 | ReviewCasePostgresIT |
| M355-03 | P0 | REJECTED → Correction | CorrectionCasePostgresIT |
| M355-04 | P0 | If-Match / 能力门禁 | decide VERSION_CONFLICT + RULE gate IT |
| M355-05 | P0 | 无 objectKey / 完整手机号 | Workspace IT + MaskedContactTest |
| M355-06 | P1 | Admin 构建与草稿单元 | npm build / test:unit |
| M355-07 | P1 | TS Client 生成消费 | agent-verify client-ts |
| M355-08 | P2 | 真实 OIDC Chrome 冒烟 | admin-pilot smoke（环境可用时） |
