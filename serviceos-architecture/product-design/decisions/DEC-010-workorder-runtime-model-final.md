# DEC-010：ServiceOS 工单运行态模型决策（最终版）

状态：Accepted  
版本：2.0.0  
日期：2026-07-24  

替代版本：

- DEC-010 V1.0

适用范围：

- ServiceOS Admin / Network / Technician
- `workorder`、`workflow`、`task`
- `forms`、`evidence`、`sla`、`operations`
- `readmodel`
- 老系统工单迁移与运行态投影

---

# 1. 决策目标

本决策冻结 ServiceOS 工单从履约方案匹配、流程实例化、节点流转、任务执行、状态投影到历史迁移的运行态模型。

核心原则：

> 工单是某个履约方案版本的一次实际执行。工单生命周期、流程位置、节点状态、任务状态和运营展示状态必须分层管理，禁止用一个工单状态字段承载全部业务语义。

---

# 2. 设计态与运行态

设计态定义“应该怎么履约”：

```text
Project
  ↓
Fulfillment Plan
  ↓
Fulfillment Version
  ↓
Fulfillment Workflow
  ↓
Fulfillment Phase
  ↓
Node / Transition
  ↓
Task / Form / Evidence / SLA / Rule
```

运行态记录“这张工单实际怎么执行”：

```text
WorkOrder
  ↓
Workflow Instance
  ↓
Node Execution
  ↓
Task Instance / System Action / Event Wait
  ↓
Form Submission / Evidence / Review / SLA / Exception
```

约束：

- 已发布版本不可修改。
- 工单创建时冻结版本。
- 后续版本发布不影响既有工单。
- 运行态不得动态读取后续被修改的草稿配置。

---

# 3. WorkOrder 定义

WorkOrder 表示一次具体的现场服务履约。

工单必须持有：

- `tenant_id`
- `project_id`
- `customer_id`
- `fulfillment_plan_id`
- `fulfillment_version_id`
- `workflow_instance_id`
- 宏观生命周期状态
- 来源系统及外部业务标识
- 创建时间、更新时间和审计信息

---

# 4. 工单创建与流程启动

```text
接收服务请求
  ↓
确定 Tenant 与 Project
  ↓
按品牌、业务类型、区域等条件匹配履约方案
  ↓
确定唯一 Active Fulfillment Version
  ↓
创建 WorkOrder
  ↓
冻结履约版本与运行快照
  ↓
创建 Workflow Instance
  ↓
激活开始节点
  ↓
开始节点自动完成
  ↓
激活第一个业务节点
```

后续动作：

- 人工作业或审核节点：创建 Task Instance。
- 系统动作节点：执行系统命令。
- 事件等待节点：注册等待事件与超时策略。
- 条件判断节点：计算唯一命中分支。

---

# 5. 一期串行运行约束

V1 Workflow Runtime 严格采用串行模型：

> 任意时刻，一个 Workflow Instance 最多只能有一个活跃 Node Execution。

一期不支持：

- 并行分叉与汇聚
- 多个同时活跃业务节点
- 跨 Phase 并行
- Fork / Join
- 多条主任务并行推进

条件和审核节点可以有多条出边，但一次运行只能选择一条。

未来确有并行业务需求时，通过新版本显式引入并行模型，不改变一期已发布版本语义。

---

# 6. 工单状态分层

## 6.1 工单生命周期

WorkOrder 权威状态，直接持久化：

- `CREATED`
- `IN_PROGRESS`
- `SUSPENDED`
- `COMPLETED`
- `CANCELLED`
- `TERMINATED`
- `CLOSED`

生命周期只在重大事件下变化。“待审核”“待安装”“等待车企回执”等不得进入生命周期枚举。

## 6.2 Workflow Instance 状态

- `NOT_STARTED`
- `RUNNING`
- `WAITING`
- `SUSPENDED`
- `COMPLETED`
- `TERMINATED`
- `FAILED`

## 6.3 Node Execution 状态

- `PENDING`
- `READY`
- `ACTIVE`
- `WAITING`
- `BLOCKED`
- `COMPLETED`
- `SKIPPED`
- `TERMINATED`

## 6.4 Task Instance 状态

- `UNASSIGNED`
- `READY`
- `CLAIMED`
- `IN_PROGRESS`
- `SUBMITTED`
- `COMPLETED`
- `REJECTED`
- `CANCELLED`

## 6.5 工单运营状态

用于列表、工作台和队列展示：

- 待分配
- 待联系客户
- 待预约
- 待现场勘测
- 待审核
- 待安装
- 安装中
- 等待外部回执
- 等待客户确认
- SLA 预警
- 异常阻塞
- 已完成

运营状态是运行事实的派生投影，不是 WorkOrder 聚合的核心生命周期状态。

---

# 7. Fulfillment Phase 运行语义

Fulfillment Phase 是业务阶段，不驱动流程。

Phase 用于：

- 工单进度展示
- 项目工作台展示
- 阶段耗时统计
- 阶段阻塞分析
- 运营指标

Phase 状态由其节点聚合：

| 节点情况 | Phase 状态 |
|---|---|
| 全部未激活 | 未开始 |
| 当前活跃节点属于该 Phase | 进行中 |
| 当前节点在该 Phase 且阻塞 | 阻塞 |
| Phase 内节点全部完成或跳过 | 已完成 |

Phase 不负责创建任务、条件判断、流转或 SLA 执行。

---

# 8. Node 与 Task

Node 是流程执行单元，Task 是责任执行单元。

```text
Business Node
  ↓
Task Instance
```

一期约束：

- 一个人工作业节点对应一个主 Task Instance。
- 一个审核节点对应一个审核 Task Instance。
- 系统动作节点不创建人工 Task。
- 事件等待节点通常不创建 Task。
- 条件、开始、结束节点不创建 Task。

一个任务内部可包含多个步骤、表单区块和证据要求，但不构成并行节点。

---

# 9. 节点完成与流转

禁止前端直接修改工单状态或当前节点。

正确链路：

```text
执行业务命令
  ↓
任务、表单、证据等完成条件满足
  ↓
生成 Node Completion Result
  ↓
Workflow Engine 匹配 Transition
  ↓
完成当前 Node Execution
  ↓
创建并激活下一 Node Execution
```

## 人工作业节点

输出业务结果，例如：

- `INSTALLABLE`
- `NOT_INSTALLABLE`
- `NEED_MORE_INFORMATION`

## 审核节点

输出：

- `PASS`
- `REJECT`
- `CANCEL`

审核节点可直接按结果出边，不必强制增加条件节点。

## 系统动作节点

输出：

- `SUCCESS`
- `FAILED`
- `RETRY_EXHAUSTED`

必须具备幂等、重试、失败分支和人工恢复边界。

## 事件等待节点

注册：

- 事件类型
- 业务关联键
- 匹配规则
- 最大等待时间
- 超时策略

匹配事件到达后完成节点并输出事件数据。

## 条件判断节点

判断来源：

- 上一节点输出
- 工单上下文
- 表单字段
- 审核结果
- 系统动作结果
- 外部事件数据

条件节点不用于判断“任务是否完成”。

---

# 10. 返工与节点重复激活

允许显式返工路径：

```text
勘测审核 REJECT
  ↓
补充勘测资料
  ↓
再次勘测审核
```

每次重新进入同一节点时必须创建新的执行记录或执行序号，并创建新的 Task Instance。

禁止重置历史节点状态覆盖返工轨迹。

---

# 11. 当前节点与当前阶段

由于一期不支持并行，Workflow Instance 可以持久化：

- `current_node_execution_id`
- `current_node_code`
- `current_phase_code`
- `workflow_state`

这些字段只能由 Workflow Engine 更新。

---

# 12. 千万级工单运营状态投影

工单列表不得在查询时联查 Workflow、Node、Task、SLA、Exception 后实时计算状态。

采用：

> 事件驱动增量计算 + 运营状态物化投影。

推荐投影：`work_order_operational_projection`

核心字段：

- `work_order_id`
- `tenant_id`
- `project_id`
- `lifecycle_state`
- `operational_state`
- `current_phase_code`
- `current_node_code`
- `current_node_name`
- `responsibility_type`
- `responsibility_id`
- `assignee_id`
- `sla_state`
- `next_deadline_at`
- `exception_level`
- `next_action_code`
- `fulfillment_version_id`
- `projection_version`
- `updated_at`

列表查询直接读取投影，不扫描运行事实表。

---

# 13. 投影更新机制

事件增量更新单张工单投影：

- `TaskAssigned` → 待执行
- `TaskStarted` → 执行中
- `ReviewTaskCreated` → 待审核
- `EventWaitRegistered` → 等待外部处理
- `SlaWarningRaised` → SLA 预警
- `OperationalExceptionOpened` → 异常阻塞
- `NodeActivated` → 更新当前 Phase、Node、责任和下一动作
- `WorkflowCompleted` → 已完成

投影消费者必须：

- 幂等
- 使用 Outbox / Inbox
- 支持 `projection_version`
- 支持单工单重建
- 支持对账
- 支持失败重试和人工恢复

---

# 14. 查询与命令边界

查询页面读取投影：

- 工单列表
- 项目运营工作台
- 风险队列
- 调度队列
- 工单 Workspace 摘要

关键命令读取领域权威状态：

- 完成任务
- 提交审核
- 暂停、恢复、取消工单
- 接收外部事件
- 执行系统动作
- 处理异常

原则：

> 读模型用于展示，领域聚合用于业务决策。

---

# 15. 工单 Workspace

打开工单后必须快速回答：

- 这是什么服务
- 当前履约阶段
- 当前业务节点
- 当前责任人
- 下一步动作
- SLA 风险或异常

推荐结构：

```text
顶部：工单摘要、生命周期、当前 Phase、当前 Node、责任、SLA、操作
左侧：业务时间线
中间：当前任务或等待事项
右侧：客户、车辆、设备、方案版本、异常等上下文
```

时间线展示业务事件，不展示原始技术日志。

---

# 16. 性能与索引

高频查询围绕活跃工单投影建立复合索引，例如：

- `(tenant_id, project_id, operational_state, next_deadline_at)`
- `(tenant_id, responsibility_id, operational_state, updated_at desc)`
- `(tenant_id, sla_state, next_deadline_at)`

建议为非终态工单建立部分索引。

历史事件、审计日志和集成尝试可按时间分区。

Redis 只能作为缓存，不能作为唯一状态权威。

---

# 17. 老系统千万级工单迁移

## 已关闭历史工单

只迁移基础事实、原状态、结果、项目、时间、必要审计和只读运营投影。

无需创建完整 Workflow、Node 和 Task 实例。

## 进行中工单

通过冻结的迁移映射规则转换：

```text
旧工单状态
  ↓
新生命周期
  ↓
当前 Phase
  ↓
当前 Node
  ↓
必要 Task Instance
  ↓
运营状态投影
```

建议使用专门的 Legacy Migration Version 或迁移配置。

## 新建工单

切换后全部使用正式运行模型。

迁移必须：

- 分批
- 小事务
- 可断点续跑
- 限速
- 记录迁移批次
- 可重复执行
- 不锁全表
- 对账后切换

---

# 18. 模块职责

- `workorder`：工单身份、版本绑定、宏观生命周期
- `workflow`：Workflow Instance、Phase 定位、Node Execution、Transition
- `task`：Task Instance、分配、领取、执行、提交
- `forms` / `evidence`：表单提交和证据实例
- `sla`：计时、预警、超时、升级
- `operations`：运营异常和恢复
- `readmodel`：运营投影、时间线、工作区、风险队列

跨模块必须通过公开 API、SPI 或领域事件协作，禁止跨模块访问 Repository 或数据库表。

---

# 19. 禁止事项

禁止：

- 一个 `work_order.status` 承载全部状态
- 将节点名称做成工单状态枚举
- 查询时实时多表计算工单列表状态
- 前端修改工单状态或当前节点
- Task 模块直接更新 Workflow 或 WorkOrder 表
- 使用 Redis 作为唯一状态权威
- 覆盖返工历史
- 一期引入多节点并行

---

# 20. 验收标准

1. 工单创建时冻结方案版本。
2. Workflow 任意时刻最多一个活跃节点。
3. 人工作业节点创建一个主任务。
4. 审核节点创建审核任务并按结果流转。
5. 系统动作支持成功、失败和重试。
6. 事件等待通过外部事件或超时完成。
7. 条件节点按业务数据命中唯一分支。
8. 返工创建新的执行记录。
9. Workflow 完成后更新工单生命周期。
10. 运营状态通过事件增量投影。
11. 投影可幂等更新、对账和单工单重建。
12. 千万级历史工单可分批迁移。

---

# End of Decision
