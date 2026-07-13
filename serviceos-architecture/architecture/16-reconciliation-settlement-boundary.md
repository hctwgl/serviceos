---
title: 对账、结算、争议与调整边界
version: 0.1.0
status: Proposed
---

# 对账、结算、争议与调整边界

## 1. 目标

试算回答“按当前事实和价格应是多少”；对账回答“双方是否认可哪些明细”；结算锁定回答“哪些确认明细进入正式财务处理”。三者不能合并成一个金额字段。

MVP 默认交付可解释双向试算和结果导出。正式对账、发票、收付款与总账由二期或外部财务系统负责；但本章先固定边界，避免试算表未来无法演进。

## 2. 核心对象

| 对象 | 职责 |
|---|---|
| `SettlementEligibility` | 判断 CalculationRun 是否可进入某结算周期 |
| `SettlementBatch` | 按方向、结算对象、项目和周期组织对账 |
| `SettlementStatement` | 提交给一个结算对象确认的明细集合 |
| `StatementLine` | 精确引用 ChargeItem/Adjustment 的不可变行 |
| `ReconciliationResponse` | 对方对单行或整单的确认/异议 |
| `DisputeCase` | 一项金额、事实或证据争议的处理闭环 |
| `Adjustment` | 补差、核减、奖励、处罚、红冲的独立修正 |
| `SettlementLock` | 防止确认后被普通重算改变的锁定记录 |
| `FinanceHandoff` | 向财务/ERP 交接的批次和回执 |

## 3. 资格

进入 Statement 前必须满足版本化 `SettlementEligibilityPolicy`：

- 方向和结算对象明确；
- CalculationRun = VALIDATED 且非 STALE/SUPERSEDED；
- 所需总部/车企审核和回传条件满足；
- 工单/服务结果符合合同口径；
- 没有阻断争议、事实冲突或未处理影响分析；
- 未被其他有效 StatementLine 重复包含；
- 业务日期属于目标周期；
- 币种、税和发票口径完整。

不满足时返回资格原因，不静默遗漏。

## 4. 方向隔离

```text
RECEIVABLE：公司向车企/客户对账并形成应收
PAYABLE：公司与网点/服务方对账并形成应付
```

Batch、Statement、审批、争议、锁定和 FinanceHandoff 全部按方向隔离。毛利报表可以关联两个方向，但不能通过修改一个 Statement 同时改应收和应付。

## 5. SettlementBatch

批次键建议：

```text
tenant
direction
settlementParty
project
period
currency
taxMode
```

Batch 状态：

```text
DRAFT -> COLLECTING -> READY -> SUBMITTED -> PARTIALLY_CONFIRMED/CONFIRMED
                                          \-> DISPUTED
CONFIRMED -> LOCKED -> HANDED_OFF
```

一个 Batch 可以生成一个或多个 Statement，适应车企/网点要求的分区域、服务类型或发票维度。

## 6. Statement 与 Line

Statement 创建时冻结：结算对象、周期、合同/项目版本、行集合、金额汇总、税、币种、模板和内容摘要。

StatementLine 精确引用：

```text
calculationRunId
chargeItemId 或 adjustmentId
workOrderId
factSetSnapshotId
pricingPlanVersionId
amount/tax/currency snapshot
lineBusinessKey
```

同一 `direction + chargeItemId/adjustmentId` 最多进入一个未作废或已锁定的有效 line。重开/重发不复制计费。

## 7. 对账交互

对方可以：

- 整单确认；
- 单行确认；
- 单行提出金额、事实、资料、责任或重复争议；
- 提供外部对账编号和证据；
- 请求核减/补差但不能直接改平台 line。

ReconciliationResponse 保存对方身份/渠道、精确 Statement 版本、行、结果、原因和原始回执。

## 8. DisputeCase

争议类型：

- `FACT_MISMATCH`；
- `EVIDENCE_REJECTED`；
- `PRICE_RULE_INTERPRETATION`；
- `DUPLICATE_CHARGE`；
- `RESPONSIBILITY`；
- `TAX_OR_INVOICE`；
- `OTHER`。

争议 Case 不拥有 assignee/SLA，关联处理 Task。解决动作必须是：确认原行、事实更正后重新试算、创建 Adjustment、作废未锁定行，或按合同保留争议到下期。

## 9. Adjustment

类型：补差、核减、奖励、处罚、退款、红冲和人工特批。

Adjustment 必须保存：

- 方向、结算对象、项目和币种；
- 原 CalculationRun/ChargeItem/StatementLine；
- 原因编码、金额、税、取整；
- 申请人、审批链、证据；
- 生效周期和是否跨期；
- 对应反向/红冲关系；
- 状态和不可变版本。

不得直接 UPDATE ChargeItem 或锁定 StatementLine。

## 10. 锁定

Statement 确认并满足政策后创建 SettlementLock：

- 锁定精确 statementVersion 和 line 摘要；
- 保存确认双方、时间、渠道和审批；
- 普通重算只产生影响提示；
- 后续差异使用 Adjustment；
- 解锁属于极高风险，默认不支持；确需纠错使用作废/红冲链而不是删除。

## 11. FinanceHandoff

ServiceOS 输出业务应收/应付明细，不拥有总账、银行支付和法定发票全生命周期。Handoff 包含：

- 锁定 Statement/Adjustment 精确版本；
- 财务主体、科目/费用映射版本；
- 币种、税和发票要求；
- 批次摘要和外部幂等键；
- 交接、接受、拒绝和错误回执；
- 财务系统单号。

交接使用 M4 OutboundDelivery 可靠模式。财务拒绝产生 OperationalException，不直接解锁业务结算。

## 12. 试算导出与正式结算

MVP 支持 `CALCULATION_EXPORT`：导出明确标记“试算/未结算”，带 run、事实和价格版本。它不能被系统视为 LOCKED Statement 或触发财务交接。

启用正式结算前必须额外满足：

- M1-05 真实价格和流程完整；
- 至少一个历史对账周期回放；
- 对账/争议/调整审批确认；
- 财务接口与会计口径确认；
- 权限、审计、锁定和红冲测试；
- 业务/财务签署启用开关。

## 13. 影响分析

事实或价格变化时：

- DRAFT/未提交 Statement：可替换为新 run 并保留差异；
- SUBMITTED/争议中：冻结自动替换，提示撤回或争议解决；
- CONFIRMED 未 LOCKED：按政策撤回；
- LOCKED/HANDED_OFF：生成 Adjustment 建议，不改变原行；
- 对上和对下分别评估。

## 14. 权限与职责分离

- 价格编辑、价格审批、事实确认、试算、结算编制、结算审批、调整审批和财务交接是独立能力；
- 同一人不能审批自己提交的高风险调整；
- 网点只能查看自己的应付 Statement；
- 客服默认不能看对下成本或修改金额；
- 导出、批量调整和红冲均增强审计。

## 15. 事件

| 事件 | 用途 |
|---|---|
| `CalculationBecameEligible` | 收集到结算批次 |
| `SettlementStatementSubmitted` | 通知对方/生成外部交付 |
| `StatementLineDisputed` | 创建 DisputeCase 和 Task |
| `SettlementStatementConfirmed` | 进入锁定检查 |
| `SettlementLocked` | 禁止普通替换、准备财务交接 |
| `AdjustmentApproved` | 纳入本期/下期 Statement |
| `FinanceHandoffAccepted` | 记录财务系统接收 |
| `SettlementImpactDetected` | 触发人工评估 |

## 16. MVP 验收

1. 不合格 run 有明确不可入账原因；
2. 同一 ChargeItem 不重复进入有效 Statement；
3. 对上和对下批次完全隔离；
4. 单行争议不直接修改金额；
5. Adjustment 关联原行和审批；
6. LOCKED 后事实更正只产生影响/调整；
7. 试算导出明确不是正式结算；
8. 财务交接幂等且保留回执；
9. 网点只能访问自身应付明细；
10. 历史对账可从事实、规则、run、line 到回执完整追溯。
