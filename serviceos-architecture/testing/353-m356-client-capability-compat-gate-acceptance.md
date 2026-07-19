---
title: M356 客户端能力兼容发布门禁验收矩阵
status: Draft
milestone: M356
lastUpdated: 2026-07-19
---

# M356 客户端能力兼容发布门禁验收矩阵

| ID | 级别 | 场景 | 期望 | 证据 |
|---|---|---|---|---|
| M356-CAP-001 | P0 | 标量 FORM（STRING/INTEGER/BOOLEAN…） | 校验通过；WEB/iOS 报告均兼容 | Unit + PostgresIT |
| M356-CAP-002 | P0 | FORM 含 ENUM/ADDRESS/SIGNATURE 等未接入类型 | 校验失败关闭；validationErrors 含中文能力说明；不得 VALIDATED | Unit + PostgresIT |
| M356-CAP-003 | P0 | EVIDENCE mediaType=SIGNATURE | 校验失败关闭 | Unit + PostgresIT |
| M356-CAP-004 | P0 | EVIDENCE mediaType=PHOTO 基线 | 校验/审批/发布通过 | PostgresIT |
| M356-CAP-005 | P0 | FORM 含 visibleWhen（H5 已支持、iOS 未共用执行器） | 校验可通过；报告中 iOS `compatible=false` 且列出缺口 | Unit + PostgresIT |
| M356-CAP-006 | P0 | publish 前复检 | APPROVED 草稿若定义被篡改为不兼容能力则发布失败 | PostgresIT |
| M356-CAP-007 | P0 | OpenAPI ConfigurationDraft.clientCompatibility | 1.0.50 契约可解析；TS client 可生成 | contracts + client-ts |
| M356-CAP-008 | P1 | Admin 设计器展示兼容报告 | 校验后可见分客户端缺口；失败错误中文可读 | Admin unit/build |
| M356-CAP-009 | P1 | 发布审计含能力摘要 | Audit 记录 blocking/missing 摘要 | Unit/IT |
| M356-CAP-010 | P0 | 不参与授权 | 能力目录变化不改变 Capability/Scope 判定 | 设计断言 + 既有授权测试不回归 |

## 明确不在本矩阵

- 灰度定向发布；
- 任务详情运行时拒单；
- iOS 条件执行器对齐。
