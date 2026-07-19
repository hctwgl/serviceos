---
title: M358 supportedClientKinds 定向发布验收矩阵
status: Implemented
milestone: M358
lastUpdated: 2026-07-19
---

# M358 supportedClientKinds 定向发布验收矩阵

| ID | 级别 | 场景 | 期望 | 证据 |
|---|---|---|---|---|
| M358-DIR-001 | P0 | 未声明 kinds + visibleWhen | 校验通过；iOS 报告缺口不阻断 | GateTest + PostgresIT（既有 M356） |
| M358-DIR-002 | P0 | WEB-only + visibleWhen | 校验/发布通过；仅 WEB 报告；侧表落库 | PostgresIT `webOnlyVisibleWhen…` |
| M358-DIR-003 | P0 | 显式声明 WEB+IOS + visibleWhen | 校验失败关闭（iOS 硬缺口） | GateTest `directedBothKinds…` |
| M358-DIR-004 | P0 | ENUM/SIGNATURE | 仍阻断（并集/子集均不支持） | 既有 Gate/IT |
| M358-DIR-005 | P0 | iOS 访问 WEB-only 已发布资产 | 422 `CLIENT_CAPABILITY_UNSUPPORTED`（目标外） | RuntimeGateTest |
| M358-DIR-006 | P0 | OpenAPI 1.0.52 | 草稿 create/update/response 含字段；client-ts | contracts + client-ts |
| M358-DIR-007 | P1 | Admin 定向目标勾选 | FORM/EVIDENCE 可见并可保存 | Designer UI |
| M358-DIR-008 | P0 | ArchitectureTest | 模块边界通过 | arch |

## 明确不在本矩阵

- Bundle CANARY；Feed 拒单；iOS 条件执行器。
