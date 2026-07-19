---
title: M335 CREATE_WORK_ORDER 强制 INBOUND Mapping 验收矩阵
status: Implemented
milestone: M335
lastUpdated: 2026-07-19
---

# M335 CREATE_WORK_ORDER 强制 INBOUND Mapping 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M335-01 | BYD 有 INBOUND Mapping | 建单成功；Canonical/Mapping 审计 | `BydCpimInboundOrderHttpPostgresIT` |
| M335-02 | REFERENCE_OEM 有 Mapping | 建单成功 | `ReferenceOemInboundOrderPostgresIT` |
| M335-03 | GEELY 本地有 Mapping | 建单成功 | `GeelyInboundCreateOrderPostgresIT` |
| M335-04 | 三 OEM 并行冒烟 | 三单均成功 | `MultiOemParallelCreateSmokePostgresIT` |
| M335-05 | Dual OEM 回归 | BYD+REF 成功 | `DualOemInboundRegressionPostgresIT` |
| M335-06 | workflow-only Bundle | 拒绝；无工单/无 COMPLETED Canonical | `rejectsWhenFrozenBundleHasNoInboundMapping` |
| M335-07 | BYD 更新/取消前置建单 | 建单仍成功 | Update/Cancel HTTP IT |
| M335-08 | 模块边界 | ArchitectureTest | ArchitectureTest |

## 明确不验收

- 删除 OEM Java Mapper、Update/Cancel Mapping 强制、吉利 Sandbox、OpenAPI/Flyway
