---
title: M367 Manual/Network assign kind 硬拒绝验收矩阵
status: Implemented
milestone: M367
lastUpdated: 2026-07-20
---

# M367 Manual/Network assign kind 硬拒绝验收矩阵

| ID | 级别 | 场景 | 期望 | 证据 |
|---|---|---|---|---|
| M367-ENG-001 | P0 | Manual assign 目标外师傅 | 422 + 无 ACTIVE 责任 | `ManualAssignClientKindRejectPostgresIT` |
| M367-ENG-002 | P0 | 拒绝审计 | DENY + `CLIENT_KIND_INCOMPATIBLE` | 同上 |
| M367-ENG-003 | P0 | Manual assign 匹配声明 | ACTIVE TECHNICIAN | 同上 |
| M367-ENG-004 | P0 | reassign 目标外 | 422；原师傅仍 ACTIVE | 同上 |
| M367-ENG-005 | P0 | 全 null 资产不滤 | 未声明师傅可派 | 同上 |
| M367-ENG-006 | P0 | Network Portal 委托路径 | 走同一 Manual 门禁 | 代码审查 + Network Portal IT 回归 |
| M367-ENG-007 | P0 | 模块边界 | ArchitectureTest | `bash scripts/agent-verify.sh arch` |
