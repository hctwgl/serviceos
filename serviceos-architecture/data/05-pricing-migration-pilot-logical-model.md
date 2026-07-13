---
title: 履约事实、试算、结算、迁移与试点逻辑数据模型
version: 0.1.0
status: Proposed
---

# 履约事实、试算、结算、迁移与试点逻辑数据模型

本文件定义 M5 逻辑实体与关键约束，不是最终 DDL。

## 1. 模块所有权

| 模块 | 拥有实体 | 不拥有 |
|---|---|---|
| 履约事实 | FactDefinition、ExtractionPolicy/Run、FulfillmentFact、FactSetSnapshot | FormSubmission/Evidence 原数据 |
| 计价 | PricingPlanVersion、ContextSnapshot、CalculationRun、ChargeItem、Comparison | 结算确认和财务支付 |
| 对账结算 | Batch、Statement、Line、Response、Dispute、Adjustment、Lock | 总账、银行和法定发票系统 |
| 迁移 | Plan、Snapshot、Batch、Record、IdMapping、Lineage、Validation | 旧系统原始数据所有权 |
| 试点 | CutoverCohort、AuthorityAssignment、GateEvaluation、CutoverDecision | 业务对象本身 |

## 2. 履约事实

### fact_definition_version

保存稳定 factCode、值类型、单位、精度、枚举、维度、用途、敏感级别、来源类型和替代关系。发布内容不可变。

### fact_extraction_policy_version

保存 source selectors、资格、转换、聚合、确认、冲突、证据和 effectiveAt 规则，以及函数库版本和摘要。

### fact_extraction_run

| 字段 | 说明 |
|---|---|
| extraction_run_id / work_order_id | 身份和工单 |
| policy_version_id | 锁定策略 |
| input_refs / input_digest | 精确来源版本 |
| status | REQUESTED/RUNNING/SUCCEEDED/PARTIAL/FAILED |
| trace_ref | 中间步骤和解释 |
| error_codes | 缺失/冲突/类型错误 |
| execution_task_id / task_execution_attempt_id | 唯一业务重试调度 |
| started_at / finished_at | 时间 |

### fulfillment_fact

保存 factCode/definitionVersion、工单、dimensionKey、类型化值、单位、effectiveAt、来源/证据 refs、run/policy、状态、替代事实、确认和摘要。

`work_order_id + fact_code + dimension_key + fact_version` 唯一。事实值和来源只追加。

### fact_correction

保存旧事实、新事实/失效、原因、证据、申请/审批和影响分析引用。

### fact_set_snapshot / fact_set_member

snapshot 保存工单、purpose、资格策略、成员数和集合摘要；member 精确引用 factId/version。成员不可更新。

## 3. 计价

### pricing_plan_version / pricing_rule

保存方向、适用范围、合同、取价日期、币种/税/舍入、规则 DAG、互斥/叠加/阶梯/封顶和函数版本。发布后不可变。

### pricing_context_snapshot

保存方向、结算对象、项目/品牌/业务/区域、合同/取价日期、税/舍入、特批上下文和摘要。

### calculation_run

| 字段 | 说明 |
|---|---|
| calculation_run_id / work_order_id | 身份 |
| direction | RECEIVABLE/PAYABLE |
| fact_set_snapshot_id | 冻结事实集合 |
| pricing_context_snapshot_id / pricing_plan_version_id | 冻结上下文与规则 |
| engine/function_library_version | 执行版本 |
| status | REQUESTED/VALIDATING/NOT_CALCULABLE/CALCULATING/CALCULATED/VALIDATED/STALE/SUPERSEDED/FAILED |
| idempotency_key / input_digest | 幂等 |
| totals | 方向、币种、税前/税/含税汇总投影 |
| execution_task_id | 自动 Task |
| created_at / completed_at | 时间 |

### charge_item / charge_item_trace

item 保存费用编码、数量、单位、单价、税、舍入、最终金额和规则节点；trace 保存条件、事实输入、中间值、调整和解释。

### calculation_impact / calculation_comparison

impact 保存事实/价格/验收变化影响的 runs/statements 和处理状态；comparison 保存两个结果的事实、规则、明细、金额和分类差异。

## 4. 对账与结算

### settlement_batch

保存方向、对象、项目、周期、币种、税模式、状态、汇总和版本。

### settlement_statement / statement_line

statement 保存不可变提交版本和摘要；line 精确引用 chargeItem 或 adjustment、run、factSet、价格版本、金额快照和 lineBusinessKey。

对同一 direction + chargeItem/adjustment 建立有效 line 排他约束，防止重复结算。

### reconciliation_response

保存 statementVersion、line/整单、外部身份/渠道、结果、原因、原始回执和时间。

### dispute_case / dispute_evidence

保存争议类型、对象、状态、handlingTaskIds、解决引用和证据。assignee/SLA 属于 Task。

### adjustment

保存类型、方向、对象、原 line/item、金额/税/币种、原因、审批、周期、红冲关系和状态。内容版本不可变。

### settlement_lock

保存 statementVersion、lineDigest、确认双方、时间、审批和 lockDigest。锁后原行不可替换。

### finance_handoff / finance_handoff_receipt

保存锁定对象、财务映射版本、payload snapshot、OutboundDelivery、外部单号和回执。

## 5. 迁移

### migration_plan_version

保存源系统、实体范围、快照策略、映射/转换版本、批次、验证、恢复点、切换和回退策略。

### source_snapshot

保存源系统、snapshot/watermark、抽取时间、表/文件清单、行数/字节数、摘要和存储引用。

### migration_batch

保存 plan、实体类型、sourceSnapshot、范围、状态、统计、开始/结束和执行版本。

### migration_record

| 字段 | 说明 |
|---|---|
| migration_record_id / batch_id | 身份 |
| source_entity_type / source_key | 来源 |
| source_record_digest | 来源摘要 |
| target_entity_type / target_id / target_version | 目标 |
| transform_status | TRANSFORMED/LOADED/VALIDATED/FAILED/SKIPPED |
| error_codes / resolution | 错误 |
| idempotency_key | 重跑幂等 |

### id_mapping

`source_system + entity_type + source_id` 唯一，保存目标 ID、合并主记录和映射状态。

### migration_lineage

保存 target object/field/version 到 source snapshot/table/record/field、转换规则和脚本版本的关系。

### migration_validation_result

保存验证类型、范围、预期/实际、差异、抽样证据、负责人、状态和签署。

## 6. 试点与切换

### cutover_cohort_version

保存项目/区域/网点/白名单/哈希比例规则、有效期、发布审批和摘要。

### work_order_authority_assignment

保存 workOrder 或创建路由键、authoritySystem（LEGACY/SERVICEOS）、cohortVersion、effectiveAt、sideEffectMode 和变更历史。同一工单同一时刻一个权威系统。

### side_effect_fence_decision

保存 operationType、workOrder、authority、authorityVersion、cohortVersion、环境、policyVersion、ALLOW/DENY、原因、短时有效期和审计。高风险副作用全部经过 fence；执行时 authorityVersion 不一致必须重新判定。

### rollout_gate_definition / rollout_gate_evaluation

definition 保存指标、窗口、阈值和数据源；evaluation 保存 cohort、时间、实际值、结果、证据和负责人。

### cutover_decision

保存阶段、cohort、GO/HOLD/EXPAND/SHRINK/ROLLBACK、门禁报告、风险、审批、时间和执行结果。

## 7. 关键约束

- FulfillmentFact、FactSetSnapshot、CalculationRun 和 ChargeItem 只追加；
- MISSING 不等于零；
- CalculationRun 明确方向、事实集合、上下文和价格版本；
- 对上/对下不共享 run、charge、statement 和 adjustment；
- LOCKED Statement 不被普通重算替换；
- 迁移目标对象可追溯到 source snapshot 和转换版本；
- 迁移重跑幂等；
- 已锁定旧金额不反推虚假事实；
- 同一工单一个 authoritySystem；
- SHADOW 模式 SideEffectFence 拒绝真实外部/财务副作用；
- rollout gate 和决策保留证据和审批。

## 8. 查询与规模验证

- 按工单/事实编码获取当前认可事实和版本链；
- 批量提取/试算和规则 trace 存储增长；
- 按方向、周期、对象汇总 charge/statement；
- 防重复 line 的并发写入；
- 历史回放 comparison 批量执行；
- 迁移批次、错误重跑和 lineage 查询；
- cohort 路由低延迟与一致性；
- SideEffectFence 判定的高可用和审计吞吐。
