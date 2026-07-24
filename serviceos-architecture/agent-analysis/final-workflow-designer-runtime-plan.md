# 最终版 Workflow Designer + 串行工单运行态实施审计

状态：设计态与串行基础已实现，运行态端到端受停止条件阻断  
风险等级：R3  
基线：`codex/final-workflow-designer-runtime`，包含 `codex/pr226-task-os` 的项目/履约工作台基础，并已合并 `origin/master`

## 1. 目标、范围与非目标

本切片只交付一条可验证链路：项目履约方案草稿在 Vue Flow 纵向设计器中完成 Phase、Node、Transition 及节点资产配置，保存到 PostgreSQL，校验、模拟并发布不可变版本；新工单冻结该版本后，由既有 `workflow` 模块按严格串行语义执行，并由读模型增量展示当前 Phase、Node、责任、SLA、下一动作和方案版本。

一期明确不实现 Fork / Join、多活跃节点、跨 Phase 并行、会签、多实例任务、任意脚本、完整 BPMN、跨流程补偿编排或新的工作流引擎。历史已发布配置和运行中的旧工单不迁移、不改写；新设计器发布的定义使用显式串行模式。

## 2. 当前已有能力

### 2.1 设计态与发布

- `configuration` 已有项目级 `cfg_project_fulfillment_profile` / `revision`，支持一个开放草稿、乐观锁、校验、预览、影响分析、发布和历史版本。
- Revision 已保存结构化 `document_json`，发布后由数据库触发器保持不可变；工单创建可冻结 `source_bundle_id` 与 `workflow_asset_version_id`。
- 通用配置中心已有不可变 Workflow、Form、Evidence、SLA 等资产和 Bundle 发布能力，无需建立第二套资产或版本引擎。
- OpenAPI 与 Admin API Client 已覆盖 Profile、Draft、Validate、Preview、Publish 和 Revision 查询。

### 2.2 运行态

- `workflow` 已有 Workflow Instance、Stage Instance、Node Instance、Task 创建、互斥网关、事件等待、定时器、Outbox/Inbox 幂等推进和完成工单能力。
- `task` 已支持人工/自动任务、执行重试、完成事件和 `resultRef`，可作为人工节点完成结果。
- `readmodel` 已有工单 Workspace、业务时间线和 Workflow 权威阶段查询基础。
- PostgreSQL Testcontainers 已覆盖线性推进、互斥分支、等待事件、配置设计发布和工单版本冻结。

### 2.3 前端

- Admin 已有项目工作台、履约方案概览、草稿页、发布页、模拟入口和统一设计令牌。
- 当前草稿页能编辑方案概要、适用范围和阶段责任；保存、刷新、校验和发布走真实 API。
- PR #226 的三栏履约工作台基础可保留并演进。

## 3. 保留内容

- 保留 Project → Fulfillment Profile → Revision 的领域边界和现有权限能力。
- 保留 `cfg_project_fulfillment_revision.document_json` 作为草稿与冻结版本的结构化载体，并由服务端提供强类型契约；不让前端直接解释任意 JSON。
- 保留通用 Configuration Asset / Bundle 的不可变发布和工单冻结链路。
- 保留 Workflow Instance / Node Instance / Task / Wait Subscription 及 Outbox/Inbox 可靠推进。
- 保留旧 Workflow 定义的历史运行兼容，但新设计器不暴露或生成并行、多实例、子流程语义。
- 保留现有项目、工单 Workspace 和产品化中文设计系统。

## 4. 删除或重构内容

- 用真实 Vue Flow Designer 替换 `FulfillmentStageDesigner` 的静态纵向阶段列表；旧组件及只服务旧草稿页的样式在调用迁移后删除。
- 将 `ProjectFulfillmentDocument` 从“阶段列表 + 外部 Workflow 引用”升级为“Phase + Node + Transition + 节点内资产快照”的单一设计事实。
- 发布不再要求配置人员先到另一处手工发布 Workflow；由履约版本发布用例从草稿图编译并冻结 Workflow 资产及所需 Bundle 引用。
- 审核节点在新串行定义中创建真正的审核任务，按 `PASS` / `REJECT` 结果选择 Transition；不复用旧 `REVIEW_TASK` 等待门闸语义。
- 系统动作节点编译为自动任务，只允许由注册的 `AutomatedTaskHandler` 执行，不创建人工任务；事件等待节点继续使用等待订阅。
- 新定义禁止 `PARALLEL_GATEWAY`、多实例和多活跃分支；旧定义和旧工单不被重写。

## 5. 前端组件结构

```text
ProjectFulfillmentDraftPage
├── WorkflowVersionBar
├── WorkflowDesignerWorkspace
│   ├── WorkflowPalette
│   ├── WorkflowPhaseOutline
│   ├── WorkflowCanvas (Vue Flow)
│   │   ├── WorkflowPhaseLane
│   │   ├── BusinessNode
│   │   └── ResultTransition
│   └── WorkflowNodeInspector
│       ├── 基础信息 / 流转规则
│       ├── 任务与责任
│       ├── 表单与证据
│       ├── SLA / 异常
│       ├── 系统动作
│       └── 事件等待
├── WorkflowValidationPanel
└── WorkflowSimulationPanel
```

设计器负责拖放、连线、选中、复制/删除、撤销/重做、纵向自动布局、缩放、适应画布和小地图。所有命令修改同一草稿模型，并只通过真实 API 保存。

## 6. 后端领域映射

| 最终模型 | 现有边界 | 增量 |
|---|---|---|
| Fulfillment Version | `cfg_project_fulfillment_revision` | 增加 Testing/Active/Archived 的可解释状态与模拟通过事实 |
| Fulfillment Phase | 草稿 `stages` | 独立 `phases`，只承担组织、展示和统计 |
| Node | Workflow 资产 `nodes` | 草稿强类型 Node，发布时编译为串行 Workflow 定义 |
| Transition | Workflow 资产 `transitions` | 草稿强类型互斥结果边，服务端校验唯一命中 |
| Task/Form/Evidence/SLA | Bundle 不可变资产 | 节点内独立草稿快照，发布时冻结并建立引用 |
| Workflow Instance | `wfl_workflow_instance` | 新定义标记 `SERIAL_V1` 并持久化当前 Node/Phase |
| Node Execution | `wfl_node_instance` | 新串行定义最多一条 ACTIVE/WAITING；返工新增执行序号 |
| System Action | 自动 Task | 复用 Task 执行尝试、租约与重试，不创建人工 Task |
| Operational Projection | 现有 readmodel Workspace | 增量更新当前 Phase/Node/责任/SLA/下一动作/版本 |

## 7. OpenAPI 缺口

- `ProjectFulfillmentDocument` 缺少 Phase、Node、Transition、画布位置、节点类型专属配置和资产快照。
- 校验问题只有 `stageCode/fieldPath`，缺少 `phaseId/nodeId/transitionId` 定位。
- 模拟 API 只覆盖方案匹配/现有 Manifest，缺少节点结果输入、预计路径、分支原因和节点资产摘要。
- 发布/Revision 返回值缺少版本生命周期、完整度、模拟状态和图定义只读视图。
- 工单 Workspace 缺少最终运营投影的完整字段及方案版本展示。

以上均采用向前演进的字段或新操作，不原地改变已发布字段含义；同步 Core OpenAPI、客户端类型和契约测试。

## 8. Flyway 缺口

- 需要连续迁移保存草稿校验/模拟通过事实和明确版本生命周期；已发布快照继续不可变。
- 需要为新串行 Workflow Instance/Node Execution 增加执行模式、当前 Node/Phase、执行序号和单活跃约束所需列/索引；约束只作用于 `SERIAL_V1`，不破坏历史并行定义。
- 系统动作复用既有自动 Task 的 claim/lease/retry/幂等记录，不新增第二套执行表。
- 需要工单运营投影表或扩展现有 readmodel 表，并建立项目、运营状态、责任和 SLA 高频索引。
- 不删除、不改写既有迁移，不给旧数据静默补造业务语义。

## 9. 运行态缺口

- 当前启动器已经按编译定义创建首个人工节点；以条件、系统动作或事件等待作为首节点的通用启动仍未覆盖。
- `SERVICE_TASK` 复用 Task 模块的自动执行尝试和 Worker，不产生人工任务；但设计器动作类型必须能解析到真实已注册 Handler。
- 现有 `REVIEW_TASK` 是等待资料审核事件的旧门闸；新设计器审核节点必须创建审核 Task，并使用完成结果流转。
- 节点完成当前以“唯一无条件出边”为主，需支持人工/审核/系统/等待节点的显式 completion result。
- 数据库尚未对新定义证明“任意时刻最多一个活跃 Node Execution”。
- Workflow 完成、返工和事件唤醒需同步当前 Node/Phase 与运营投影。

## 10. 投影缺口

- 当前列表/Workspace 已有若干聚合查询，但没有完整的 `lifecycle + operational state + current phase + current node + responsibility + SLA + deadline + next action + fulfillment version` 单行物化投影。
- 新投影消费者必须按事件幂等增量更新，记录 `projection_version`，提供单工单重建与对账入口；列表只读投影，关键命令继续读领域权威状态。

## 11. 测试与验收计划

1. 单元测试：文档映射、图校验、完整度、编译确定性、互斥分支、循环风险和模拟路径。
2. PostgreSQL IT：草稿保存刷新、节点资产快照、发布不可变、Active 新工单冻结、串行单活跃约束、审核 PASS/REJECT、系统动作重试、等待事件/超时、返工执行序号、完成工单和投影幂等。
3. 契约：Core OpenAPI lint/兼容、生成客户端与前端 API 测试。
4. 前端：Designer 命令/撤销重做/校验定位单测，三应用及共享包完整门禁。
5. 浏览器：真实 PostgreSQL/Backend/Admin 登录态，按任务给定的比亚迪山东家充数据完成设计态和运行态；在 1440×900、1280×800 保存关键截图并检查控制台。
6. 仓库门禁：`frontend`、`compile`、`arch`、`contracts origin/master`、`docs`、`git diff --check`，最后尽可能运行 `verify-local.sh`。

## 12. 风险与停止条件

- 若新需求必须引入并行、改变 Project/Plan/Version 边界、绕过 Modulith、用 Mock/localStorage、破坏已发布契约、删除历史迁移或无法隔离旧工单，则停止扩大实现。
- 历史并行 Workflow 通过“旧定义继续原引擎、新发布定义显式 `SERIAL_V1`”隔离；若迁移检查发现无法安全区分，停止运行态迁移并报告。
- 外部车企真实生产调用不是本切片授权范围。端到端使用仓库已有受控测试 Connector/真实入站回执 API，保留签名、幂等和 Inbox/Outbox，不伪造外部生产成功。
- R3 改动若任一精准 PostgreSQL IT、架构或契约门禁失败，不进入浏览器验收和发布声明。

## 13. 实施后发现与停止结论

- 设计态已经由真实 Vue Flow、PostgreSQL 草稿、服务端校验、持久化模拟和不可变发布闭环；新发布 Workflow 标记为 `SERIAL_V1`，PostgreSQL 部分唯一索引证明单活跃节点。
- 节点内 Form、Evidence、SLA 草稿在发布时物化为当前版本独立资产，并写入冻结 Bundle；刷新和后续公共模板变化不会改写已发布版本。
- 真实比亚迪测试工单已经证明 Active 版本冻结、Workflow 启动、联系客户与预约勘测严格顺序推进，以及 Workspace 从冻结定义展示当前 Phase/Node。
- 全旅程未继续宣称完成：审核节点目前继承通用现场派单策略，尚无经产品确认的“项目运营审核责任”资产映射；`提交车企` 也不能安全复用现有 `integration.byd.submit-review` Handler，因为该 Handler 的业务键要求真实 `OutboundDelivery`，而设计器系统节点只有 WorkOrder 上下文。直接增加 noop Handler、伪造 OutboundDelivery 或把审核派给现场工程师都会违反失败关闭和领域边界。
- 因上述两项属于核心责任规则与真实外部副作用契约，已按任务停止条件停止扩大实现。后续需要负责人确认审核责任资产模型，并接受一个由 Workflow 上下文创建/引用 OutboundDelivery 的 integration SPI 后，才能继续完成 PASS、系统动作、回执和 WorkOrder `COMPLETED` 的最终浏览器闭环。
