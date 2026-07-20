---
title: M366 派单级 supportedClientKinds 过滤验收矩阵（设计门禁）
status: Proposed
milestone: M366
lastUpdated: 2026-07-20
---

# M366 派单级 supportedClientKinds 过滤验收矩阵（设计门禁）

本矩阵在 ADR-088 **Accepted** 前只验收设计完备性；Accepted 后追加工程行。

## 设计门禁（当前）

| ID | 级别 | 场景 | 期望 | 证据 |
|---|---|---|---|---|
| M366-DES-001 | P0 | A1～A5 决策包成文 | ADR-088 Proposed 含推荐与替代 | ADR-088 |
| M366-DES-002 | P0 | 与 M358 求交规则说明 | A4-R 交集 / null 不滤 | ADR-088 §3 |
| M366-DES-003 | P0 | 师傅 clientKind 权威来源 | A2-R 声明能力，非请求头唯一权威 | ADR-088 |
| M366-DES-004 | P0 | 未接受前不改 latestMilestone | implementation-status 仍为 M365 | 本 PR |

## 工程门禁（ADR Accepted 后填写）

| ID | 级别 | 场景 | 期望 | 证据 |
|---|---|---|---|---|
| M366-ENG-001 | P0 | 自动池剔除目标外师傅 | 不激活 TECHNICIAN | IT（待写） |
| M366-ENG-002 | P0 | 过滤后为空 → MANUAL | 可解释原因码 | IT（待写） |
| M366-ENG-003 | P0 | 资产全 null 不施加 kind 过滤 | 与 M358 缺省一致 | IT（待写） |
| M366-ENG-004 | P0 | 执行门禁仍保留 | M359/M357 回归 | 既有 IT |
