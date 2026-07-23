---
title: 履约方案匹配与版本绑定验收矩阵（目标）
status: Accepted
lastUpdated: 2026-07-23
relatedDecisions: [DEC-007, DEC-003, ADR-091]
relatedDesign: [AD-014]
---

# 履约方案匹配与版本绑定验收矩阵（目标）

本矩阵是 [DEC-007](../product-design/decisions/DEC-007-multi-plan-fulfillment-and-work-order-version-binding.md) 与 [AD-014](../architecture/AD-014-fulfillment-plan-matching-and-version-binding.md) 的目标验收场景，用于后续实现里程碑对照。当前工程基线为 `ProjectFulfillmentProfile`（按 serviceProductCode 键，见 M378～M383 已实现验收矩阵），结构化匹配、优先级、具体度、待确认与重匹配/调整命令尚未实现，本矩阵状态为 `Accepted`（目标），不代表已通过测试。

## 1. 领域与版本约束

| ID | 场景 | 期望 |
|---|---|---|
| FPM-01 | 一个项目创建多个履约方案 | 各方案独立存在，code 项目内唯一 |
| FPM-02 | 同一方案创建第二个草稿 | 失败关闭（每个方案最多一个 DRAFT） |
| FPM-03 | 同一方案存在第二个 ACTIVE | 数据库唯一约束拒绝 |
| FPM-04 | 发布方案草稿 | 原 ACTIVE 原子转 HISTORICAL，DRAFT 转 ACTIVE，activeVersionId 更新、draftVersionId 清空 |
| FPM-05 | 删除被工单引用的 HISTORICAL 版本 | 拒绝 |
| FPM-06 | A 方案发布失败 | 不影响 B 方案的 ACTIVE 与 DRAFT |

## 2. 匹配算法

| ID | 场景 | 期望 |
|---|---|---|
| FPM-10 | 候选范围 | 仅项目下 ENABLED 且有 ACTIVE 版本的方案；DRAFT/HISTORICAL 不参与自动匹配 |
| FPM-11 | 结构化硬匹配 | 任一维度不满足即排除；方案该维度未约束视为通配 |
| FPM-12 | 优先级排序 | 按 matchPriority 降序 |
| FPM-13 | 具体度排序 | 同优先级按命中的已约束维度数（具体度）降序 |
| FPM-14 | 唯一命中 | 绑定该方案 ACTIVE 版本，保存匹配解释与候选集合 |
| FPM-15 | 禁止随机决定 | 不得用数据库第一条/创建最早/ID 最小/列表顺序决定 |

## 3. 冲突与无匹配

| ID | 场景 | 期望 |
|---|---|---|
| FPM-20 | 允许重叠 | 优先级或具体度可得唯一结果时发布仅提示覆盖关系 |
| FPM-21 | 必须阻止冲突 | 两方案可产生相同优先级且相同具体度结果时，发布失败并指出冲突方案/字段/场景 |
| FPM-22 | 运行时同级多命中 | 不随机选择，进入“待确认履约方案”，保存原始候选与解释 |
| FPM-23 | 零命中 | 进入“待确认/履约配置异常”，展示未匹配原因，不默认第一条方案，不建残缺流程实例 |

## 4. 人工指定与匹配模式

| ID | 场景 | 期望 |
|---|---|---|
| FPM-30 | 人工指定合法方案 | 校验方案归属/ENABLED/有生效版本/权限/生命周期后绑定，模式 MANUAL |
| FPM-31 | 人工指定规则不匹配 | 必须填写例外原因，模式 EXCEPTION，保留原始自动候选 |
| FPM-32 | 匹配模式取值 | AUTO / MANUAL / EXCEPTION / REMATCH |

## 5. 工单绑定与版本隔离

| ID | 场景 | 期望 |
|---|---|---|
| FPM-40 | 受理绑定 | 工单保存 fulfillmentPlanId + fulfillmentPlanVersionId（及匹配审计字段） |
| FPM-41 | 运行读取 | 流程/SLA/表单/证据/验收统一读 fulfillmentPlanVersionId，不读“当前生效版本” |
| FPM-42 | 版本隔离 | 发布新版本后旧工单仍执行绑定版本，历史工单可查原版本内容 |

## 6. 受理后更换方案

| ID | 场景 | 期望 |
|---|---|---|
| FPM-50 | 已受理未派网点 Rematch | 重新匹配、替换版本、重生成 SLA、重建流程实例、保留审计 |
| FPM-51 | 已派网点未开工 Adjust | 重校验责任网点/师傅、SLA 重算、任务取消、数据处理与责任链事务一致 |
| FPM-52 | 现场作业已开始 | 禁止直接更换方案/版本；走终止重建或异常履约变更；不得直接改 DB fulfillmentPlanVersionId |

## 7. 责任链不变

| ID | 场景 | 期望 |
|---|---|---|
| FPM-60 | 匹配时点 | 履约方案匹配发生在责任网点分配之前 |
| FPM-61 | 责任链 | 平台分配责任网点、网点分配责任师傅、网点不能跨网点转单不变 |
