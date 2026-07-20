---
title: M366 派单级 supportedClientKinds 过滤验收矩阵
status: Implemented
milestone: M366
lastUpdated: 2026-07-20
---

# M366 派单级 supportedClientKinds 过滤验收矩阵

## 决策门禁

| ID | 级别 | 场景 | 期望 | 证据 |
|---|---|---|---|---|
| M366-DES-001 | P0 | A1～A5 决策包成文并接受 | ADR-088 Accepted：A1-R～A5-R | ADR-088 |
| M366-DES-002 | P0 | 与 M358 求交规则说明 | A4-R 交集 / null 不滤 | ADR-088 §3 |
| M366-DES-003 | P0 | 师傅 clientKind 权威来源 | A2-R 声明能力，非请求头 | ADR-088 + V134 |

## 工程门禁

| ID | 级别 | 场景 | 期望 | 证据 |
|---|---|---|---|---|
| M366-ENG-001 | P0 | 自动池剔除目标外师傅 | 不激活 TECHNICIAN | `DispatchClientKindFilterPostgresIT` |
| M366-ENG-002 | P0 | 过滤后为空 → MANUAL | `error_code=CLIENT_KIND_TARGET_EMPTY` | 同上 |
| M366-ENG-003 | P0 | 资产全 null 不施加 kind 过滤 | 未声明师傅仍可自动派 | 同上 |
| M366-ENG-004 | P0 | 未声明 + 定向目标 | MANUAL + 原因码 | 同上 |
| M366-ENG-005 | P0 | 匹配声明 → APPLIED | ACTIVE TECHNICIAN | 同上 |
| M366-ENG-006 | P0 | FORM∩EVIDENCE 求交 | 单元覆盖 | `DefaultFrozenBundleClientCapabilityProbeTest` |
| M366-ENG-007 | P0 | 执行门禁仍保留 | 不改 M357～M363 | 代码审查 / 既有 IT |
| M366-ENG-008 | P0 | 模块边界 | ArchitectureTest | `bash scripts/agent-verify.sh arch` |
