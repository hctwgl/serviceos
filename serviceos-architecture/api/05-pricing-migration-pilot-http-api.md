---
title: 履约事实、试算、结算、迁移与试点 HTTP API
version: 0.1.0
status: Proposed
---

# 履约事实、试算、结算、迁移与试点 HTTP API

沿用现有认证、数据范围、幂等、`If-Match`、异步 operation、Problem Details 和增强审计约定。价格、调整、迁移和切换 API 属于高风险管理能力。

M5 默认 feature set 为 `FACTS + SHADOW_PRICING + CALCULATION_EXPORT + MIGRATION_PILOT`。第 3～4 节正式结算 API 是二期预留契约，只有 `FORMAL_SETTLEMENT` 按 tenant/project/direction/cohort 激活后才可调用；未激活返回 `FEATURE_NOT_ENABLED`，且不得创建领域对象。

## 1. 履约事实

| 方法与路径 | 命令/用途 | 关键载荷 | 成功 |
|---|---|---|---|
| `POST /work-orders/{id}/fact-extraction-runs` | RequestFactExtraction | policyVersionId、sourceRefs? | 202 |
| `GET /fact-extraction-runs/{id}` | 输入、trace、事实和错误 | — | 200 |
| `GET /work-orders/{id}/fulfillment-facts` | 当前/历史事实 | factCode、status、asOf? | 200 |
| `POST /fulfillment-facts/{id}:confirm` | ConfirmFact | reason?、evidenceRefs? | 200 |
| `POST /fulfillment-facts/{id}:correct` | CorrectFact | newValue/sourceRefs、reason、approvalRef | 202（同步激活 fact guard 与 run holds） |
| `POST /work-orders/{id}/fact-set-snapshots` | CreateFactSetSnapshot | purpose、memberFactIds? | 201 |
| `GET /fact-set-snapshots/{id}` | 成员、资格与摘要 | — | 200 |

默认由服务端按 purpose 选择事实；客户端指定 memberFactIds 时仍执行同工单、状态、维度、必需事实、证据和权限校验。

## 2. 试算

| 方法与路径 | 命令/用途 | 关键载荷 | 成功 |
|---|---|---|---|
| `POST /calculation-runs` | RequestCalculation | workOrderId、direction、factSetSnapshotId | 202 |
| `GET /calculation-runs/{id}` | 状态、汇总、错误和 trace 链接 | — | 200 |
| `GET /calculation-runs/{id}/charge-items` | 费用明细 | — | 200 |
| `GET /charge-items/{id}/explanation` | 事实、规则、中间值和舍入 | — | 200 |
| `POST /calculation-comparisons` | CompareCalculations | baselineRunRef、candidatePricingPlanVersionId、classificationPolicy、overrideApprovalRef | 202 |
| `GET /calculation-comparisons/{id}` | 差异结果 | — | 200 |
| `POST /calculation-runs/{id}:export` | ExportCalculation | format、purpose | 202 |

### 2.1 RequestCalculation

```json
{
  "workOrderId": "WO-001",
  "direction": "RECEIVABLE",
  "factSetSnapshotId": "FACT-SET-10"
}
```

服务端依据工单锁定的 ConfigurationBundle、方向、有效合同、结算关系、区域和取价日期事实，解析唯一 PricingPlanVersion 与 PricingContextSnapshot；普通调用方不能提交或覆盖这些字段。解析零命中/多命中分别失败并保留候选解释。运行模式也由服务端依据方向级计价权威和 feature gate 决定，M5 默认生成 `SHADOW`，客户端不能自行声明 `AUTHORITATIVE`。缺失必需事实返回一个 `NOT_CALCULABLE` run 及原因，不创建零金额“成功”结果。

创建 run 前服务端锁定并检查 FactSetSnapshot 全部成员的 `FactEligibilityGuard`。存在更正 pending 时普通请求返回 `FACT_CORRECTION_PENDING`；受权影子分析即使继续，也只能生成 `PENDING_FACT_INPUT` 的 SHADOW run。

候选价格实验只能通过 `POST /calculation-comparisons` 引用已批准的候选版本，由具备 `pricing.shadowOverride` 的主体发起。由候选 override 产生的 run 强制标记 `SHADOW`，保存 override 审批和审计，永远不具备正式结算资格。

## 3. 结算资格与 Statement

本节所有写命令（包括 eligibility 的“可进入正式结算”结论）受 `FORMAL_SETTLEMENT` feature gate 和方向级计价权威门禁约束。M5 可执行只读/影子资格诊断，但结果必须标记 `SHADOW_NOT_SETTLEABLE`。

| 方法与路径 | 命令/用途 | 关键载荷 | 成功 |
|---|---|---|---|
| `POST /calculation-runs/{id}:evaluate-settlement-eligibility` | EvaluateEligibility | policyVersionId、period | 200 |
| `POST /settlement-batches` | CreateSettlementBatch | direction、party、project、period、currency | 201 |
| `POST /settlement-batches/{id}:collect` | CollectEligibleRuns | query/policyVersion | 202 |
| `GET /settlement-batches/{id}` | 状态、汇总和 Statements | — | 200 |
| `POST /settlement-batches/{id}/statements` | CreateStatement | lineRefs、templateVersion | 201 |
| `POST /settlement-statements/{id}:submit` | SubmitStatement | channel、recipientRef | 202 |
| `POST /settlement-statements/{id}:confirm` | ConfirmStatement | statementVersion、externalRef?、evidenceRefs? | 200 |
| `POST /settlement-statements/{id}:lock` | LockStatement | approvalRef | 200 |
| `GET /settlement-statements/{id}` | 版本、行、响应、争议和锁 | — | 200 |

Collect 使用排他 lineBusinessKey，重复执行不重复纳入 ChargeItem。

## 4. 对账、争议和调整

本节全部写命令受 `FORMAL_SETTLEMENT` feature gate 约束。

| 方法与路径 | 命令/用途 | 关键载荷 | 成功 |
|---|---|---|---|
| `POST /internal/reconciliation-responses` | RecordResponse（连接器/受信服务） | inboundEnvelopeId、statementVersion、lineDecisions | 200 |
| `POST /statement-lines/{id}:dispute` | OpenDispute | type、reason、evidenceRefs | 201 |
| `GET /dispute-cases/{id}` | 争议、Task 和解决历史 | — | 200 |
| `POST /dispute-cases/{id}:resolve` | ResolveDispute | resolutionType、domainActionRef、evidenceRefs | 200 |
| `POST /adjustments` | RequestAdjustment | type、direction、sourceLineRef、amount/tax、reason | 202 |
| `POST /adjustments/{id}:approve` | ApproveAdjustment | decision、note | 200 |
| `POST /settlement-statements/{id}/finance-handoffs` | CreateFinanceHandoff | mappingVersionId | 202 |

内部对账回执必须先经过 M4 InboundEnvelope/CanonicalMessage。ResolveDispute 不能直接修改 line 金额，只能引用事实更正、新试算、Adjustment 或确认原行等真实动作。

## 5. 迁移

| 方法与路径 | 命令/用途 | 关键载荷 | 成功 |
|---|---|---|---|
| `POST /migration-plans/{id}:validate` | ValidateMigrationPlan | sampleRefs? | 202 |
| `POST /source-snapshots` | RegisterSourceSnapshot | source、watermark、manifest、digest | 201 |
| `POST /migration-batches` | CreateMigrationBatch | planVersionId、snapshotId、entityType、range | 201 |
| `POST /migration-batches/{id}:execute` | ExecuteMigrationBatch | dryRun、limits | 202 |
| `POST /migration-batches/{id}:retry-failures` | RetryFailedRecords | errorCodes?、approvalRef? | 202 |
| `GET /migration-batches/{id}` | 统计、错误和验证 | — | 200 |
| `GET /migration-records/{id}/lineage` | 源到目标血缘 | — | 200 |
| `POST /migration-validations` | RunMigrationValidation | scope、checks | 202 |
| `POST /migration-validations/{id}:sign-off` | SignOffValidation | role、decision、note | 200 |

API 不提供直接任意 SQL 或跳过 lineage 的导入。dryRun 不写正式领域对象。

## 6. cohort、权威系统与灰度

| 方法与路径 | 命令/用途 | 关键载荷 | 成功 |
|---|---|---|---|
| `POST /cutover-cohorts` | CreateCohortDraft | routingRules、sideEffectMode | 201 |
| `POST /cutover-cohorts/{id}:publish` | PublishCohort | approvalRef、effectiveAt | 200 |
| `POST /cutover-cohorts/{id}:evaluate` | PreviewCohort | sampleBusinessKeys | 200 |
| `GET /work-orders/{id}/authority` | 当前权威系统和 cohort | — | 200 |
| `POST /cutover-decisions` | RequestCutoverDecision | cohortVersion、decision、gateReportRef、reason | 202 |
| `POST /cutover-decisions/{id}:approve` | ApproveCutover | decision、conditions | 200 |
| `GET /rollout-gates/evaluations` | 门禁结果 | cohort、window | 200 |
| `POST /side-effect-fence:check` | CheckFence（内部） | workOrder、operationType、environment | 200 |

SideEffectFence 检查是副作用执行前的强制内部能力，普通客户端不能以请求参数声明自己属于 PILOT_ACTIVE。

该端点也是诊断/集成契约，不是可长期复用的“允许令牌”。实际副作用执行必须使用当前 authority/cohort/policyVersion 再次判定，并把 decision、authorityAssignmentId、authorityVersion 与 fencePolicyVersion 记录到 Delivery/Notification/AssignmentActivation/Settlement 动作；权威版本变化后旧 decision 不得继续执行。

所有会修改工单及其子域对象的普通命令必须携带 `X-Work-Order-Authority-Version`（内部消息使用等价 envelope 字段）。领域事务提交前再次校验当前 assignment；SHADOW、READ_ONLY、旧版本或非权威系统命令返回冲突且不写业务对象/Outbox。切换时旧版本进入 DRAINING 并排空未决副作用，不能依赖入口路由作为唯一防线。

## 7. 回退

| 方法与路径 | 命令/用途 | 关键载荷 | 成功 |
|---|---|---|---|
| `POST /cutover-decisions/{id}:prepare-rollback` | PrepareRollback | targetWatermark、reason | 202 |
| `GET /rollback-plans/{id}` | 已发生副作用、反向同步和阻塞项 | — | 200 |
| `POST /rollback-plans/{id}:execute` | ExecuteRollback | approvalRef、confirmedWatermark | 202 |
| `POST /rollback-plans/{id}:verify` | VerifyRollback | checks、evidenceRefs | 200 |

存在无法安全反向同步的副作用时，ExecuteRollback 返回阻塞，不自动双写旧系统。

## 8. 错误码补充

| 错误码 | HTTP | 含义 |
|---|---:|---|
| `FACT_SOURCE_CONFLICT` | 422 | 多来源冲突且无决策策略 |
| `REQUIRED_FACT_MISSING` | 422 | 必需事实缺失 |
| `FACT_SET_INELIGIBLE` | 422 | 事实集合不具备目的资格 |
| `CALCULATION_REQUEST_INVALID` | 422 | 请求的方向、版本或上下文格式非法 |
| `PRICING_CONTEXT_NOT_FOUND` | 422 | 未找到适用合同/结算对象/价格方案 |
| `PRICING_CONTEXT_AMBIGUOUS` | 422 | 合同/取价上下文多命中 |
| `FACT_CORRECTION_PENDING` | 409 | 相关试算因事实更正处于结算资格冻结 |
| `FEATURE_NOT_ENABLED` | 403/404 | 正式结算等能力尚未按范围启用 |
| `SHADOW_RESULT_NOT_SETTLEABLE` | 422 | 影子/候选结果不能进入正式结算 |
| `SETTLEMENT_LINE_DUPLICATE` | 409 | 费用已进入有效 Statement |
| `SETTLEMENT_LOCKED` | 409 | 锁定结果不可普通修改 |
| `MIGRATION_IDEMPOTENCY_CONFLICT` | 409 | 同源记录摘要改变 |
| `CUTOVER_DUAL_WRITE_FORBIDDEN` | 409 | 同一工单试图双主写入 |
| `WORK_ORDER_AUTHORITY_CHANGED` | 409 | 命令携带的 authorityVersion 已过期或系统非权威 |
| `SHADOW_WRITE_FORBIDDEN` | 403 | 影子/只读实例试图写业务事实 |
| `SIDE_EFFECT_FENCE_DENIED` | 403/409 | 当前环境/cohort 不允许副作用 |
| `ROLLBACK_NOT_SAFE` | 422 | 已发生副作用无法安全反向同步 |

## 9. 事件目录

| 事件 | 关键载荷 |
|---|---|
| `FulfillmentFactConfirmed` | fact、definition/policy、source refs |
| `FulfillmentFactCorrected` | old/new fact、reason、impact ref |
| `CalculationCompleted` | run、direction、fact set、pricing version、totals |
| `CalculationNotCalculable` | run、missing/conflict reasons |
| `SettlementLocked` | statement version、line digest、approval |
| `MigrationBatchCompleted` | plan/snapshot/batch、counts、validation summary |
| `WorkOrderAuthorityAssigned` | business key/work order、cohort、authority、mode |
| `CutoverDecisionApproved` | cohort、decision、gate report、effectiveAt |
| `SideEffectFenceDenied` | workOrder、operation、authority、policy |

事件不包含完整价格规则、用户个人信息或迁移原始记录。
