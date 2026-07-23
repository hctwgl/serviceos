---
title: AD-014 履约方案匹配与版本绑定
status: Accepted
lastUpdated: 2026-07-23
relatedDecisions: [DEC-007, DEC-003, ADR-091, ADR-002, ADR-020]
supersedesScope: 项目级唯一履约配置解析（改为方案级匹配与绑定）
---

# AD-014 履约方案匹配与版本绑定

状态：Accepted（架构设计已接受；结构化匹配、优先级、具体度、待确认与重匹配/调整命令尚未实现，不得标记 IMPLEMENTED）

本文件是 [DEC-007 项目多履约方案、独立版本与工单绑定](../product-design/decisions/DEC-007-multi-plan-fulfillment-and-work-order-version-binding.md) 的工程设计，详细描述领域模型、匹配算法、发布与绑定事务边界。它承接 ADR-091 的 `ProjectFulfillmentProfile`，并把项目级唯一配置解析（[05-configuration-version-center](05-configuration-version-center.md) 第 8 节）在产品层收敛为方案级匹配。

## 1. 目标与非目标

目标：

- 明确「项目 → 履约方案 → 履约方案版本 → 工单绑定」的领域模型与不变量；
- 定义确定性、可解释、可校验的履约方案匹配算法；
- 定义方案版本原子发布、工单受理绑定、受理后重匹配/调整的事务边界；
- 保持责任链（平台派网点、网点派师傅）与失败关闭语义不变。

非目标（本阶段不做）：任意脚本表达式/无约束规则引擎、跨项目方案继承、存量工单自动批量迁移、灰度/定时/分区域发布、AMOUNT 加权计价。

## 2. 领域模型

### 2.1 FulfillmentPlan（履约方案）

```text
FulfillmentPlan
  id
  projectId
  code                项目内唯一
  name
  description
  status              ENABLED | DISABLED | ARCHIVED
  matchPriority       整数，越大优先级越高
  activeVersionId     -> FulfillmentPlanVersion(ACTIVE)，可空
  draftVersionId      -> FulfillmentPlanVersion(DRAFT)，可空
  createdAt / createdBy / updatedAt / updatedBy
```

一个项目可有多个 FulfillmentPlan，代表不同现场服务场景。方案是业务编排与生命周期载体，不新建第二套 Workflow/Form/Evidence/SLA/Expr 引擎（承接 ADR-091 第 3 点）。

### 2.2 FulfillmentPlanVersion（履约方案版本）

```text
FulfillmentPlanVersion
  id
  fulfillmentPlanId
  versionNo
  status              DRAFT | ACTIVE | HISTORICAL
  matchRuleSnapshot           结构化适用范围
  workflowDefinitionSnapshot  流程定义
  slaPolicySnapshot           SLA 规则
  dispatchPolicySnapshot      派单策略、候选网点范围、技能/资质要求
  formRequirementSnapshot     表单模板与字段规则
  evidenceRequirementSnapshot 图片/附件/证据要求
  acceptancePolicySnapshot    验收规则
  notificationPolicySnapshot  通知与升级规则
  settlementPolicySnapshot    结算规则引用
  sourceVersionId             复制来源版本
  contentDigest               整版本快照摘要
  publishedAt / publishedBy / createdAt / createdBy
```

版本冻结整份履约配置快照。快照可以继续引用既有 `ConfigurationRelease`/`ConfigurationBundle` 作为机器运行模型（见第 8 节），但工单读取以方案版本为唯一入口。

### 2.3 结构化适用范围 matchRuleSnapshot

第一阶段仅支持结构化字段，保证可校验、可查询、可解释，不引入任意脚本：

```text
业务类型 businessType
服务子类型 serviceSubType
客户品牌 brandCode
产品或设备分类 deviceCategory
行政区域 regionScope（省/市/区）
故障等级 faultLevel（可多值）
工单优先级 priority
保内/保外 warrantyScope
工单来源 source
客户等级 customerGrade
```

每个维度可为「未约束（通配）」或「一个/多个明确取值」。未约束维度不参与硬匹配排除，但降低具体度。

## 3. 匹配算法

### 3.1 候选范围

```text
当前工单所属项目
+ 方案状态 = ENABLED
+ 存在 ACTIVE 版本
```

只用每个方案的当前 `ACTIVE` 版本参与新工单匹配；`DRAFT` 与 `HISTORICAL` 不参与自动匹配。

### 3.2 匹配步骤

```text
1. 确定工单所属项目
2. 查询项目下所有 ENABLED 且有 ACTIVE 版本的方案
3. 读取各方案 ACTIVE 版本的 matchRuleSnapshot
4. 对每个结构化维度执行硬匹配：工单取值必须落在方案该维度的取值集合内，或方案该维度未约束
5. 排除任一维度不满足的方案
6. 对剩余方案按 matchPriority 降序排序
7. 同 matchPriority 时按规则具体度降序排序
8. 若唯一 → 绑定该方案及其 ACTIVE 版本
9. 若仍非唯一或为空 → 进入待确认/异常处理（第 6 节）
10. 保存匹配结果、候选集合与匹配解释
```

禁止使用「数据库返回第一条、创建时间最早、方案 ID 最小、前端列表顺序」决定方案。

### 3.3 具体度定义

具体度 = 该方案版本 `matchRuleSnapshot` 中「已明确约束（非通配）」且被当前工单命中的维度数量。维度越多、约束越明确，具体度越高。示例：

```text
方案 A：业务类型=维修                                → 具体度 1
方案 B：业务类型=维修, 品牌=比亚迪, 设备=充电设备, 故障等级=紧急 → 具体度 4
```

同 `matchPriority` 时选择具体度更高者。具体度只用于辅助排序，不得掩盖无法解释的配置冲突（见第 5 节）。当 `matchPriority` 与具体度都相同且候选大于一时，判为运行时冲突，进入待确认。

## 4. 匹配时点与工单生命周期

匹配发生在正式受理，而非草稿创建：

```text
DRAFT(工单草稿) → 补充业务字段 → SubmitAcceptance
→ 校验项目与服务范围 → MatchFulfillmentPlan → 绑定 ACTIVE 版本
→ 创建 SLA 实例 → 创建流程实例 → 进入派单
```

`ActivateWorkOrder`/受理命令内完成匹配与绑定；草稿阶段字段变化不触发匹配。

## 5. 发布期冲突检查

发布方案版本时执行规则冲突检查：

- 允许重叠：若两方案适用条件重叠，但 `matchPriority` 或具体度可得出确定结果（如「普通维修 优先级 50」与「紧急抢修 业务类型=维修 且 故障等级=紧急 优先级 100」），发布时可提示覆盖关系，不阻止发布。
- 必须阻止：若两个方案能在同一工单产生「相同 `matchPriority` 且相同具体度」的匹配结果，阻止发布，并明确指出冲突方案、冲突字段、可能同时命中的场景，建议修改优先级或适用范围。

## 6. 无匹配与运行时冲突

- 零命中：不得创建不完整流程实例，不得默认项目第一条方案。工单进入「待确认履约方案」或「履约配置异常」。页面展示未匹配原因（项目无已发布方案 / 品牌不在任何方案服务范围 / 业务类型不受支持 / 设备类型未配置 / 服务区域未覆盖）。
- 运行时多命中（同优先级同具体度）：不得随机选择，进入待确认队列，由具备权限的客服经理、项目经理或平台管理员处理；保存原始候选与匹配解释。
- 有权限用户可补充/修正工单字段、修正项目履约配置、重新执行匹配，或在有权限并填写原因时人工指定方案。

## 7. 匹配模式与命令

匹配模式：`AUTO`（自动）、`MANUAL`（人工指定）、`EXCEPTION`（异常处置指定）、`REMATCH`（重新匹配）。

命令与事务边界：

| 命令 | 适用阶段 | 事务内动作 | 关键校验 |
|---|---|---|---|
| `MatchFulfillmentPlan` | 正式受理 | 匹配 → 绑定 planId+versionId → 建 SLA/流程实例 → 写匹配解释 → 一次提交 | 唯一命中；否则待确认，不建流程 |
| `ManualAssignFulfillmentPlan` | 待确认/异常 | 校验后绑定 → 建 SLA/流程实例 → 记录原始候选与原因 | 方案属本项目、ENABLED、有 ACTIVE 版本、用户有权限、生命周期允许、不匹配需例外原因 |
| `RematchFulfillmentPlan` | 已受理未派网点 | 重新匹配 → 替换版本绑定 → 重生成 SLA → 重建流程实例 → 审计 | 阶段允许；责任网点尚未分配 |
| `AdjustFulfillmentPlan` | 已派网点未开工 | 重校验责任网点/师傅是否符合新方案 → 清空/更换责任人 → SLA 重算 → 取消已建任务 → 处理已填数据 → 与责任链事务一致 | 专用高风险 capability；现场未开工 |

现场作业已开始：禁止直接更换方案或版本；走终止重建或异常履约变更流程；不得直接修改数据库 `fulfillmentPlanVersionId`。

## 8. 与配置资产层的关系

`ConfigurationRelease`/`ConfigurationBundle`（asset 版本原子集合）继续作为机器运行模型存在。方案版本发布时把解析后的资产版本快照纳入 `*Snapshot` 字段并可绑定一个 Bundle；工单绑定 `fulfillmentPlanVersionId` 后，通过该版本间接锁定所有资产版本。产品层匹配单位是「履约方案版本」，配置资产层解析单位是「Bundle」，两者一一对应、同一事务冻结。

原「项目级 `ConfigurationResolver` 按 brand/product/region/businessDate 解析唯一 Release」的语义收敛为：结构化条件先匹配唯一方案，方案的 ACTIVE 版本再确定唯一 Bundle。

## 9. 版本发布事务

```text
1. 锁定履约方案或乐观并发校验
2. 校验当前草稿完整性
3. 校验匹配规则冲突（第 5 节）
4. 校验流程图完整性
5. 校验 SLA、表单、派单规则引用
6. 原 ACTIVE 版本 → HISTORICAL
7. 当前 DRAFT → ACTIVE
8. 更新 activeVersionId，清空 draftVersionId
9. 写发布审计
10. 写领域事件 / Outbox
11. 整个事务一次提交
```

失败时不得出现：两个 ACTIVE、无 ACTIVE 但旧版本已失效、部分规则已发布部分仍旧版本、流程版本与 SLA 版本不一致。数据库层建立唯一约束保证每个方案最多一个 `DRAFT`、一个 `ACTIVE`（不依赖应用层）。

## 10. 工单绑定字段

```text
WorkOrder
  projectId
  fulfillmentPlanId
  fulfillmentPlanVersionId
  workflowInstanceId
  （增强审计，可选）fulfillmentPlanVersionNo / fulfillmentPlanVersionDigest
                    matchedAt / matchMode / matchedBy / matchExplanation
```

运行读取统一走 `fulfillmentPlanVersionId`；禁止运行时读取方案「当前 ACTIVE 版本」。历史工单必须能查询原方案版本内容。

## 11. 模块边界

- `configuration` 模块拥有 FulfillmentPlan/Version 的编排、发布与解析端口，对外只暴露 `configuration::api`；
- 工单受理在 `workorder` 应用服务内调用 configuration 的匹配端口，绑定结果写入工单聚合，同事务创建 SLA/流程实例；
- 匹配算法不依赖 Web、具体车企协议或前端顺序；
- 派单在责任网点分配之前只消费方案版本快照中的候选网点范围与要求，不改变责任链。

## 12. 现状与实现差距

当前 `ProjectFulfillmentProfile` 按 `serviceProductCode` 键、`ProjectFulfillmentResolver` 按维度失败关闭解析、工单冻结 `fulfillment_profile_id`/`fulfillment_revision_id`/`fulfillment_version`。相对本设计尚缺：`matchPriority` 与结构化 `matchRuleSnapshot`、具体度排序、`HISTORICAL` 状态、待确认队列、AUTO/MANUAL/EXCEPTION/REMATCH 模式、`RematchFulfillmentPlan`/`AdjustFulfillmentPlan` 命令、方案级 code/name 与项目内多方案。以上为接受的目标，按独立里程碑实施；本文件不构成已实现声明。
