---
title: MVP 范围与实施路线
version: 0.1.0
status: Proposed
---

# MVP 范围与实施路线

## 1. MVP 目标

用一个可代表复杂度的车企项目和一个高业务量项目，验证同一套核心领域模型能通过配置支撑勘安主链路，并能完整追踪资料、审核、派单、SLA、外部回传和基础试算。

MVP 成功不是“旧系统功能全部迁移”，而是证明：

- 新项目差异可由版本化配置表达；
- 在途工单不会因配置发布而漂移；
- 自动派单和回传失败可转人工闭环；
- 资料逐项审核与多版整改可追溯；
- 对上/对下试算可从履约事实解释和重算。

## 2. 建议范围

### 必须包含

- 项目、品牌、业务产品和配置包；
- 外部工单接入、幂等、映射和原始报文留存；
- 工单、阶段、任务、动作和时间线；
- 自动分配跟进人、自动派网点和人工兜底；
- 网点人工/自动派师傅；
- 勘测预约、勘测提交、审核与整改；
- 安装预约、安装提交、审核与整改；
- 动态节点表单和条件资料要求；
- 单项资料驳回、补传版本和标准驳回原因；
- 节点 SLA、预警、超时与升级；
- 车企回传、失败重试和人工修复；
- 履约事实与对上/对下基础试算；
- 品牌、区域、网点、参与关系的数据权限；
- 操作审计和配置发布审计。

### 延后到二期

- 完整仓储、调拨、旧件和买损；
- 正式账期、发票、收付款、红冲和复杂对账；
- 投诉、返工、收费维修的完整应用；
- GIS 路线优化和师傅实时调度；
- 全自动 AI 审核与智能派单；
- 通用页面设计器和跨行业模板市场。

## 3. 七个里程碑

| 里程碑 | 核心输出 | 退出条件 |
|---|---|---|
| M0 架构基线 | 宪法、领域、能力、ADR、术语 | 业务/产品/技术共同评审 |
| M1 业务资产基线 | 一个车企完整流程、字段、资料、SLA、派单和价格样本 | 可用真实样本做桌面演练 |
| M2 履约内核 | 工单、任务、配置包、权限、事件和审计 | 正常链路 API/集成测试通过 |
| M3 现场作业 | 预约、勘测、安装、资料、审核、整改 | 师傅端到总部审核闭环 |
| M4 自动化闭环 | 派单、SLA、回传、重试、人工兜底 | 典型失败场景全部可恢复 |
| M5 试算与试点 | 履约事实、双向试算、迁移、灰度 | 真实项目并行运行并核对 |
| M6 研发实施基线 | 工程模块、事务消息、安全/NFR、部署和交付计划 | 工程就绪 Gate 与首个纵向切片计划通过 |

M1 的填写说明和模板见 [M1 业务资产基线填写手册](../research/01-m1-business-asset-pack.md)。M1 不是一次普通访谈：每个口径必须关联合同、接口、历史工单或负责人确认，并使用真实样本进行桌面演练。

## 4. 开发前置材料

正式编码业务模块前，至少取得：

1. 一个项目近 3～6 个月脱敏工单及状态日志；
2. 正常、取消、改派、驳回、补传、二次上门、回传失败等样本；
3. 节点字段和资料要求的可执行矩阵；
4. 派单硬规则、评分口径和历史结果；
5. SLA 具体时长和日历；
6. 对上和对下至少一套真实报价、对账和争议样本；
7. 外部接口文档、原始报文、幂等和回执样本；
8. 角色、数据范围和敏感字段清单。

在上述材料中，首个试点项目、可执行流程/动作矩阵、字段与资料矩阵、SLA 数值、外部接口样本以及至少一套对上/对下价格样本属于核心业务编码的阻塞项。在这些材料确认前，只能实施身份、权限、审计、文件、配置版本框架等不依赖业务口径的基础能力。

## 5. 质量门槛

- 所有关键命令有幂等策略；
- 所有自动节点有失败策略和人工接管者；
- 所有配置发布有静态校验、审批、版本和回滚方案；
- 所有金额计算保留输入事实、规则版本、中间步骤和取整方式；
- 所有敏感数据有最小权限、脱敏和导出审计；
- 所有状态迁移由领域命令完成，不允许页面直接改状态字段；
- 正常链路、异常链路和恢复链路都纳入验收。

M2 的架构输入见 [配置资产与版本中心](../architecture/05-configuration-version-center.md)、[工单、任务与流程执行内核](../architecture/06-work-order-task-execution-kernel.md)、[身份、授权与审计](../architecture/07-identity-authorization-audit.md)、[核心命令与事件契约](../api/01-command-event-contracts.md)、[HTTP API 基线](../api/02-work-order-task-http-api.md)、[逻辑数据模型](../data/01-execution-logical-model.md)和[M2 验收矩阵](../testing/01-m2-execution-acceptance-matrix.md)。这些文档通过评审后，仍需结合 M1 真实项目基线形成模块级验收用例。

M3 的架构输入见 [预约与现场作业](../architecture/08-appointment-field-operations.md)、[动态表单与字段引擎](../architecture/09-dynamic-form-field-engine.md)、[资料、审核与整改闭环](../architecture/10-evidence-review-correction.md)、[现场作业 HTTP API](../api/03-field-operations-http-api.md)、[现场作业逻辑数据模型](../data/03-field-operations-logical-model.md)和[M3 验收矩阵](../testing/02-m3-field-operations-acceptance-matrix.md)。M3 开工前必须用首个试点项目填写 M1-02/M1-03，并提供真实脱敏表单、资料示例、驳回原因和移动端弱网约束。

M4 的架构输入见 [服务网络与派单引擎](../architecture/11-service-network-dispatch.md)、[SLA 时钟、预警与升级](../architecture/12-sla-clock-escalation.md)、[车企集成、回传与可靠交付](../architecture/13-integration-reliability.md)、[通知与运营异常中心](../architecture/14-notification-operational-exception.md)、[M4 HTTP API](../api/04-automation-integration-http-api.md)、[M4 逻辑数据模型](../data/04-automation-integration-logical-model.md)和[M4 验收矩阵](../testing/03-m4-automation-integration-acceptance.md)。M4 开工前必须填写 M1-04/M1-06，确认签约比例口径、SLA 数值/日历和至少一家试点车企的真实脱敏接口报文。

M5 的架构输入见 [履约事实提取与双向试算运行时](../architecture/15-fulfillment-fact-calculation-runtime.md)、[对账、结算、争议与调整边界](../architecture/16-reconciliation-settlement-boundary.md)、[历史数据迁移、双轨与切换](../architecture/17-data-migration-cutover.md)、[试点、灰度发布与可观测性](../architecture/18-pilot-rollout-observability.md)、[M5 HTTP API](../api/05-pricing-migration-pilot-http-api.md)、[M5 逻辑数据模型](../data/05-pricing-migration-pilot-logical-model.md)和[M5 验收矩阵](../testing/04-m5-pricing-migration-pilot-acceptance.md)。M5 开工前必须完成 M1-05/M1-08、试点项目选择、历史金额样本、迁移盘点和切换/回退职责确认。

M6 的工程输入见 [研发工程、模块与应用服务实施蓝图](../architecture/19-engineering-module-blueprint.md)、[事务、消息、幂等、并发与后台执行实施蓝图](../architecture/20-transaction-messaging-concurrency-blueprint.md)、[安全、非功能、部署与运维实施蓝图](../architecture/21-security-nfr-deployment-blueprint.md)、[M6 研发交付计划](01-m6-engineering-delivery-plan.md)和[M6 工程就绪验收矩阵](../testing/05-m6-engineering-readiness-acceptance.md)。M6 允许先搭建不依赖业务口径的工程基础，但进入核心业务编码仍受 M1 真实样本阻塞。

## 6. 首批待决策事项

1. MVP 首个车企与第二对照项目；
2. 现有系统并行运行和切换策略；
3. 流程引擎采用成熟组件还是轻量自建编排层；
4. 动态字段采用关系模型、JSONB 还是混合模型；
5. 资产/仓储与外部系统的主数据边界；
6. 对上/对下结算首期做到试算还是正式结算；
7. 后台、网点端、师傅端的技术栈和离线要求。
