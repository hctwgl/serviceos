---
title: 服务网络与派单引擎设计
version: 0.1.0
status: Proposed
---

# 服务网络与派单引擎设计

## 1. 目标

派单引擎回答两个不同问题：

1. 哪些网点或师傅具备承接资格；
2. 在合格候选中，哪一个最适合本次工单。

第一步是不可被评分覆盖的硬过滤，第二步是可解释的排序与业务调节。人工指定和改派必须显式记录原因，不能悄悄绕过停派、黑名单和安全资质。

## 2. 核心对象

| 对象 | 职责 |
|---|---|
| `ServiceNetwork` | 服务网点及其状态、组织和结算关系 |
| `ServiceCoverage` | 品牌、区域、业务类型和地理范围授权 |
| `ServiceCapability` | 桩型、业务类型、技能和资质能力 |
| `CapacityPolicy` | 在途量以及日/周/月承接上限 |
| `NetworkMetricSnapshot` | 履约率、评分、质量、负荷等指标快照 |
| `DispatchRequest` | 一次待分配网点或师傅的请求 |
| `DispatchDecision` | 一次不可变候选评估和选择结果 |
| `CandidateEvaluation` | 单个候选的过滤、评分和解释 |
| `ServiceAssignment` | 生效的网点或师傅责任关系 |
| `DispatchPolicyAdjustment` | 对额度、比例或策略的受审调整 |

## 3. 两级派单

```mermaid
flowchart LR
  A["工单需要现场服务"] --> B["网点派单请求"]
  B --> C["网点硬过滤"]
  C --> D["网点评分排序"]
  D --> E["网点 ServiceAssignment"]
  E --> F["师傅派单请求"]
  F --> G["师傅资格/状态过滤"]
  G --> H["自动或网点人工选择"]
  H --> I["师傅 ServiceAssignment"]
```

网点和师傅使用同一决策框架，但候选数据、策略和权限不同。网点派单属于总部/项目能力，师傅派单可以由系统或当前网点负责人执行。

## 4. 网点准入

### 4.1 硬过滤

当前已确认的硬条件：

- 网点处于启用且未停派状态；
- 不在当前项目/用户/区域黑名单；
- 工单地址在其行政服务区域或显式覆盖范围；
- 负责相应品牌和业务类型；
- 部分车企要求的资质有效；
- 当前在途量未超过该业务类型上限；
- 指定网点仍满足不可覆盖的安全和状态规则。

后续可配置但首期不默认启用：地图服务半径、库存满足、节假日营业、原安装网点维修优先、偏远专项授权。

每个过滤结果保存规则编码、策略版本、输入值摘要、通过/排除和原因。候选被排除后不再参与评分。

### 4.2 不可覆盖规则

以下规则默认不可由普通人工指定覆盖：

- 网点已清退、停用或合同失效；
- 命中强制黑名单；
- 法定/安全资质缺失；
- 无相应业务能力；
- 数据范围不允许该项目或区域。

如确需特批，使用独立高风险审批，不修改原规则结果，并记录授权依据和有效期。

## 5. 候选评分

通过过滤后按配置化指标计算：

```text
candidateScore = Σ(normalizedMetric × weight) + policyAdjustments
```

首批指标：

- 当前在途量与剩余产能；
- 历史履约率；
- 网点评分；
- 质量、资料驳回和超时表现；
- 月度签约派单比例缺口。

签约比例只能调节合格候选，不能压过停派、黑名单、资质、产能或最低质量门槛。履约较差时允许低于签约比例。

## 6. 签约比例

月度比例需要明确的统计口径：工单数、金额或加权业务量。该口径尚未由业务确认，因此作为策略配置和 M1 阻塞项，不在代码中固定。

比例调节建议保存：

```text
period
committedShare
actualShare
eligibleVolume
gap
qualityAdjustment
resultingDispatchBias
```

达到目标后降低调节分，不直接永久停派；未达到目标也不能绕过质量和产能。

## 7. 容量

MVP 按“当前在途量 + 业务类型”控制上限。模型预留日、周、月上限，但只有完成真实口径调研后才能启用。

容量判断使用一致性快照：

- 决策时读取候选的 `capacityVersion`；
- 创建 ServiceAssignment 时原子校验/预占；
- 竞争失败重新计算或选择下一候选；
- 取消、改派或完成时释放预占；
- 不依赖最终一致报表值作为硬容量判断。

## 8. DispatchDecision

一次决策保存：

```text
dispatchDecisionId
requestId / workOrderId / taskId
targetType: NETWORK/TECHNICIAN
policyVersionId
inputSnapshotDigest
candidateEvaluations[]
selectedCandidateId
selectionMode: AUTO/MANUAL_OVERRIDE/DIRECTED
selectedBy / reason / approvalRef
createdAt
```

`CandidateEvaluation` 保存过滤命中、指标快照、标准化值、权重、调整项、最终分和排序。系统必须能输出“为什么选中”和“为什么没有选择其他候选”。

决策只追加；改派创建新 DispatchRequest、Decision 和 ServiceAssignment，不覆盖旧决策。

## 9. ServiceAssignment

ServiceAssignment 表示一段生效责任关系：

```text
assignmentId
workOrderId / taskId
assigneeType / assigneeId
sourceDecisionId
effectiveFrom / effectiveTo
status
supersedesAssignmentId
reassignmentReason
```

同一责任层级同一时刻最多一个有效 ServiceAssignment。改派事务必须同时关闭旧 ServiceAssignment、建立新 ServiceAssignment、更新参与关系并写 Outbox 事件。

原网点改派后失去当前工单访问权；历史审计和其自己产生的履约记录仍按授权策略保留可追溯性。

## 10. 人工指定与改派

已确认可人工指定/改派角色：项目经理、项目助理和客服。执行时：

1. 权威服务端重新跑硬过滤；
2. 显示指定候选不合格原因；
3. 普通可覆盖的评分结果允许人工选择；
4. 不可覆盖规则要求特批；
5. 改派原因必填；
6. 产生旧/新 ServiceAssignment、权限和通知事件；
7. 当前业务中改派不直接影响考核或结算，但记录完整事实供未来规则使用。

## 11. 自动派单失败

失败类型：

- `NO_ELIGIBLE_CANDIDATE`；
- `CAPACITY_RACE_LOST`；
- `POLICY_CONFIGURATION_ERROR`；
- `METRIC_UNAVAILABLE`；
- `ASSIGNMENT_WRITE_CONFLICT`；
- `DEPENDENCY_UNAVAILABLE`。

可重试错误由任务模块按失败策略调度。无候选或策略错误生成 `OperationalException` 和项目经理人工处理 Task，目标 24 小时内处理并提前预警。

当前业务在无可派网点时不自动跨区。人工处理可以显式选择经批准的跨区或兜底方案。

## 12. 师傅派单

师傅硬条件至少包含：

- 属于当前网点且账号有效；
- 业务技能/资质满足且未过期；
- 当前可工作状态；
- 无不可接受的排班或预约冲突；
- 工单数据范围允许；
- 部分项目的品牌/桩型技能要求。

MVP 支持自动选择或网点人工分配，不做实时路线最优化。人工选择同样创建 DispatchDecision，不能只更新 `technician_id`。

## 13. 指标快照与口径

指标服务提供带版本和统计截止时间的快照。派单保存实际使用的快照摘要，避免后来指标变化导致无法解释历史选择。

指标缺失策略必须配置：排除、使用中性值或转人工。高风险硬指标不得默认中性值。

## 14. 策略调整

项目经理可以申请临时调整网点在途上限或月度比例，但需要审批。`DispatchPolicyAdjustment` 保存原值、新值、业务范围、生效区间、原因、申请/审批和到期恢复。

调整不直接修改历史策略版本；运行时在基础发布策略上应用有效的受审调整，并在决策解释中显示。

## 15. 事件

| 事件 | 用途 |
|---|---|
| `DispatchRequested` | 启动决策任务 |
| `DispatchCandidateEvaluated` | 可选审计/调试，不默认广播完整敏感指标 |
| `DispatchCompleted` | 创建责任关系和后续任务 |
| `DispatchFailed` | 重试或人工接管 |
| `ServiceAssignmentActivated` | 权限投影、通知、容量预占 |
| `ServiceAssignmentEnded` | 权限撤销、容量释放 |
| `ServiceNetworkReassigned` | 时间线、通知和外部同步 |
| `DispatchPolicyAdjusted` | 策略缓存失效和审计 |

## 16. MVP 验收

1. 硬过滤不能被评分和签约比例覆盖；
2. 同区域多网点可生成完整候选解释；
3. 并发容量预占不会超过在途上限；
4. 指定网点仍执行不可覆盖规则；
5. 自动失败重试耗尽后生成项目经理人工 Task 和 24 小时 SLA；
6. 无可派网点不自动跨区；
7. 改派关闭旧 ServiceAssignment 并撤销原网点当前访问权；
8. 网点自动/人工派师傅都保留 Decision；
9. 策略调整经过审批、到期恢复且进入解释；
10. 同一输入快照和策略版本产生确定性候选排序。
