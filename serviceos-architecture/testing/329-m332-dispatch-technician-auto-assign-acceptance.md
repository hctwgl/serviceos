---
title: M332 DISPATCH TECHNICIAN 自动指派验收矩阵
status: Implemented
milestone: M332
lastUpdated: 2026-07-19
---

# M332 DISPATCH TECHNICIAN 自动指派验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M332-01 | 有师傅 + TECHNICIAN 容量 | ACTIVE NETWORK + ACTIVE TECHNICIAN；TECH APPLIED 审计 | `DispatchPolicyServiceAssignmentPostgresIT` |
| M332-02 | 无师傅夹具 | NETWORK ACTIVE；TECHNICIAN MANUAL；无 TECH 行 | 同上 |
| M332-03 | NETWORK 无容量 | 无任何 assignment；NETWORK MANUAL（回归 M324） | 同上 |
| M332-04 | Inbox 幂等 | 重复消费不双写 | Inbox SUCCEEDED |
| M332-05 | 模块边界 | ArchitectureTest | ArchitectureTest |

## 明确不验收

- targetType schema、地图 scope、自动改派、吉利联调、OpenAPI
