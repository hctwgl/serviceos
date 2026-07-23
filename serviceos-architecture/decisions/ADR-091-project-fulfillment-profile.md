---
title: ADR-091 项目工单类型履约配置（ProjectFulfillmentProfile）
status: Accepted
date: 2026-07-20
---

# ADR-091：项目工单类型履约配置

## 状态

Accepted

## 背景

ServiceOS 已具备不可变配置资产与 ConfigurationBundle 冻结，但运营缺少「项目 + 服务产品（工单类型）→ 履约方案」的产品化编排层。项目详情仍指向技术配置设计器空壳，建单仅按 brand/product/province 解析 Bundle。

## 决策

1. 新增 `ProjectFulfillmentProfile` / `ProjectFulfillmentRevision`，UI 名称为「工单类型与履约配置」。
2. 正式字段使用 `serviceProductCode`（ServiceProduct）；UI 文案可用「工单类型」。
3. Profile 负责业务编排与生命周期；**不**新建第二套 Workflow/Form/Evidence/SLA/Expr/Bundle 引擎。
4. 发布产生不可变 Revision + 服务端编译 Manifest + contentDigest；并绑定既有 Bundle。
5. 正式建单权威解析改为 `ProjectFulfillmentResolver`；无匹配失败关闭。
6. 工单额外冻结 `fulfillment_profile_id` / `fulfillment_revision_id` / `fulfillment_version`；历史工单 `LEGACY_BUNDLE`。
7. 能力细分：`project.fulfillment.*`，禁止单一项目管理权限覆盖全部动作。

## 后果

- 正面：运营可按项目配置多工单类型；新旧工单版本隔离可解释；
- 成本：入站建单需种子 Profile；需维护 Manifest 与 Bundle 一致性；
- 限制：V1 不做自由 BPMN、跨项目继承、存量批量升级。

## 演进（DEC-007 / AD-014）

本 ADR 的 `ProjectFulfillmentProfile` / `ProjectFulfillmentRevision` 是「履约方案 / 履约方案版本」的当前实现。项目负责人已接受 DEC-007，将其演进为：一个项目多个可按结构化条件匹配的履约方案；每个方案维护 DRAFT/ACTIVE/HISTORICAL 版本；工单在正式受理时按 matchPriority 与规则具体度匹配唯一方案并绑定其生效版本；无法唯一确定时进入待确认状态。匹配算法、领域模型与事务边界见 [AD-014](../architecture/AD-014-fulfillment-plan-matching-and-version-binding.md)。

结构化匹配、matchPriority、具体度、待确认状态、AUTO/MANUAL/EXCEPTION/REMATCH 模式与 Rematch/Adjust 命令为接受的目标方向，尚未实现，不得据此声明 IMPLEMENTED。
