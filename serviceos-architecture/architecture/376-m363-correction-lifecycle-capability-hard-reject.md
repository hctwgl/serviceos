---
title: M363 整改领取/启动能力硬拒单
status: Implemented
milestone: M363
lastUpdated: 2026-07-20
relatedMilestones: [M359, M362, M361, M266]
openapiVersion: "1.0.56"
flywayVersion: "131"
---

# M363 整改领取/启动能力硬拒单

## 目标

关闭 M362 遗留的 API 旁路：不兼容 TECHNICIAN_* 仍可调用 claim/start 推进状态。
对齐 Product/08「不允许执行到现场中途才发现必需能力缺失」与 M359 任务详情硬拒同构。

## 范围与非目标

- 范围：
  - `claim` / `start` 在 HumanTask 状态迁移**之前**调用
    `FrozenBundleClientCapabilityProbe.requireCompatible`（源 Task 冻结 Bundle）；
  - OpenAPI 1.0.56 登记 claim/start 422；
  - 单元证明不调用 `humanTasks.claim/start`；控制器安全测试 422。
- 明确不做：
  - 列表整表 422 或隐藏整改（仍为 M362 软注解）；
  - Network Portal on-behalf 门禁；
  - iOS 条件执行器 / 派单过滤 / REVIEW_TASK；
  - UNKNOWN 强制升级；Flyway 新迁移（仍为 131）。

## 事实源

- Product/08 §8：不兼容必须安全失败，禁止中途发现
- M359 任务详情头硬拒
- M362 列表软注解与 Probe 复用
- M253 UNKNOWN 迁移窗口

## 设计要点

1. 硬拒发生在状态迁移前，避免 CLAIMED/RUNNING 半状态后再失败。
2. 与列表注解同源 Probe；资料路径仍由 M361 RuntimeGate 覆盖。
3. 源 Task 缺失时不在此发明额外 404（沿用既有 requireCase / 资料路径语义）。

## 已实现

- `DefaultTechnicianCorrectionService#requireSourceTaskClientCompatible`
- claim/start 入口调用
- OpenAPI 1.0.56
- ServiceTest + ControllerSecurity

## 明确未实现

- Network Portal on-behalf；iOS 执行器；派单过滤；REVIEW_TASK 模板分离。

## 验证命令

```bash
bash scripts/agent-verify.sh test DefaultTechnicianCorrectionServiceTest,TechnicianCorrectionControllerSecurityTest
bash scripts/agent-verify.sh arch
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh client-ts
bash scripts/agent-verify.sh docs
```
