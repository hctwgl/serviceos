---
title: M332 DISPATCH 自动激活 TECHNICIAN ServiceAssignment
status: Implemented
milestone: M332
lastUpdated: 2026-07-19
relatedMilestones: [M324, M306, M331]
---

# M332 DISPATCH 自动激活 TECHNICIAN ServiceAssignment

## 目标

在 M324 ACTIVE NETWORK 之后，用同一冻结 `dispatchPolicyRef` 对网点内师傅候选求值，
激活 ACTIVE TECHNICIAN（protocol v1）；空池/无容量失败关闭为 TECHNICIAN MANUAL，保留 NETWORK。

## 范围与非目标

- 范围：
  - `ActivateTechnicianFromFrozenDispatchCommand` + `activateTechnicianFromFrozenDispatchPolicy`
  - `DefaultTaskDispatchPolicyEventConsumer`：NETWORK 成功后解析 `NetworkPortalTechnicianQuery`
  - 容量：`dsp_capacity_counter` responsibility=`TECHNICIAN`，assignee=`technicianProfileId`
  - 审计：`SERVICE_DISPATCH_TECHNICIAN_POLICY_APPLIED` / `_MANUAL`
  - PostgreSQL IT 扩展：`DispatchPolicyServiceAssignmentPostgresIT`
- 明确不做：
  - schema `targetType` / 双 DISPATCH 资产
  - ServiceCoverage / 地图 / 比例分配
  - 自动改派、OpenAPI / Admin 工作台
  - Flyway（复用现有表）

## 已实现

- NETWORK→TECHNICIAN 两段自动派单 + IT

## 明确未实现

- Mapping DSL、入站仅 Mapping、低代码深化、吉利联调

## 验证命令

```bash
bash scripts/agent-verify.sh it DispatchPolicyServiceAssignmentPostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```
