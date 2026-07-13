---
title: ServiceOS 术语表
version: 0.1.0
status: Draft
---

# 术语表

| 术语 | 英文/代码建议 | 定义 | 不等同于 |
|---|---|---|---|
| 客户主体 | Client | 与公司签约或对接的车企等商业主体 | 品牌 |
| 品牌 | Brand | 业务、组织或数据管理维度 | 运营项目 |
| 运营项目 | Project | 特定合同周期、区域、团队和配置组合 | 车企本身 |
| 服务产品 | ServiceProduct | 可被履约的标准业务，如勘测、安装、维修 | 流程版本 |
| 服务请求 | ServiceRequest | 外部或内部提出的服务诉求 | 已受理工单 |
| 工单 | WorkOrder | 平台接受后用于组织一次履约的容器 | 单一任务、流程状态 |
| 阶段 | Stage | 可用于业务汇总的履约阶段 | 工单生命周期 |
| 任务 | Task | 有责任人、输入、完成条件和 SLA 的工作单元 | 页面待办投影 |
| 动作 | Action | 特定角色在特定任务上可执行的命令 | 状态 |
| 领域事件 | DomainEvent | 已经发生且有业务意义的事实 | 命令、日志文本 |
| 资料要求 | EvidenceRequirement | 某节点需要提交的结构化证据定义 | 文件类型 |
| 资料槽位 | EvidenceSlot | 任务运行时按条件解析出的具体资料要求位置 | 静态模板定义 |
| 逻辑资料 | EvidenceItem | 同一资料槽位中的一项稳定资料身份 | 某次上传文件 |
| 资料版本 | EvidenceRevision | 逻辑资料的一次不可变文件/数据版本 | 可覆盖附件 |
| 资料集合快照 | EvidenceSetSnapshot | 冻结某次提交、审核、报告或回传所使用资料版本的不可变集合 | 动态“当前附件列表” |
| 审核案例 | ReviewCase | 保存审核对象、结论、驳回原因与历次决策的业务对象 | 责任人和 SLA |
| 审核决定 | ReviewDecision | 对精确资料、表单或作业版本作出的一次不可变决定 | 可覆盖审核状态 |
| 整改案例 | CorrectionCase | 汇总被驳回对象、整改要求和多轮补传的闭环对象 | 修改原审核决定 |
| 外部审核回执 | ExternalReviewReceipt | 车企对某个 ReviewCase 返回的一次不可变结果记录 | 第二种审核案例模型 |
| 联系尝试 | ContactAttempt | 一次通过电话等渠道联系用户的事实记录 | 工单最后联系状态 |
| 预约 | Appointment | 对某项现场服务时间、地点和参与方的承诺聚合 | 实际到场记录 |
| 预约修订 | AppointmentRevision | 一次不可变预约约定，改约产生新修订 | 覆盖原预约时间 |
| 上门访问 | Visit | 一次实际到场、现场停留和离场记录 | Appointment |
| 现场操作 | FieldOperation | 在 Visit 中执行的勘测、安装、维修或整改工作 | 工单本身 |
| 标准字段 | FieldDefinition | 具有稳定编码、类型和业务语义的字段目录项 | 某项目页面标签 |
| 表单版本 | FormVersion | 已发布、不可变的节点表单 Schema 和规则集合 | 可编辑草稿 |
| 表单提交 | FormSubmission | 一次不可变的任务表单提交版本 | 核心领域对象全部事实 |
| 移动工作包 | MobileWorkPackage | 为设备和任务签发的最小离线配置与数据快照 | 完整工单数据库副本 |
| 服务覆盖 | ServiceCoverage | 网点可承接的品牌、项目、业务类型和区域范围版本 | 当前工单分配 |
| 派单请求 | DispatchRequest | 一次需要为工单/任务选择网点或师傅的执行请求 | 最终责任关系 |
| 派单决定 | DispatchDecision | 一次不可变候选过滤、评分和选择结果 | 直接修改网点字段 |
| 候选评估 | CandidateEvaluation | 单个候选的硬规则、指标、得分和排除解释 | 只有最终总分 |
| 服务责任分配 | ServiceAssignment | 网点或师傅在明确生效区间承担工单/任务的关系 | TaskAssignment 或派单算法结果本身 |
| SLA 策略 | SlaPolicyVersion | 已发布的目标、日历、暂停、预警和升级规则 | 单一预计完成时间 |
| SLA 实例 | SlaInstance | 针对具体工单/任务运行的版本化业务时钟 | 流程引擎定时器 |
| SLA 时间片 | SlaClockSegment | RUNNING 或 PAUSED 的不可变时间区间 | 一个 paused 布尔字段 |
| SLA 升级 | SlaEscalation | SLA 里程碑触发的一次收件人解析和通知记录 | 人工异常处理案例 |
| 出站交付 | OutboundDelivery | 锁定业务对象版本和 payload 的一次外部交付意图 | 单次 HTTP 请求 |
| 交付尝试 | DeliveryAttempt | OutboundDelivery 的一次网络或文件发送尝试 | 新的业务交付 |
| 外部确认 | ExternalAcknowledgement | 外部系统对交付返回的技术或业务结果 | 本地发送成功 |
| 通知意图 | NotificationIntent | 由领域事件和通知计划生成的一次业务通知目的 | 单条供应商短信请求 |
| 运营异常 | OperationalException | 自动流程无法继续且需要恢复验证的运营问题 | 工单生命周期状态 |
| 履约事实 | FulfillmentFact | 经确认、可用于计价的标准事实 | 原始表单 JSON |
| 事实提取运行 | FactExtractionRun | 按明确来源和策略生成事实的一次可追踪执行 | 直接读取当前表单值 |
| 事实集合快照 | FactSetSnapshot | 某次试算冻结使用的不可变事实版本集合 | 动态“当前事实”查询 |
| 费用明细 | ChargeItem | 规则对履约事实计算的可解释金额项 | 结算单总额 |
| 试算运行 | CalculationRun | 使用明确事实和价格版本执行的一次可重现计算 | 已确认结算单 |
| 价格方案版本 | PricingPlanVersion | 已发布且不可变的一组适用范围与计价规则 | 可直接修改的报价表 |
| 价格上下文快照 | PricingContextSnapshot | 锁定方向、对象、合同、取价日期、税和舍入的一次试算上下文 | 项目当前配置 |
| 对账批次 | SettlementBatch | 按方向、对象、项目、周期组织的对账集合 | 单张工单金额 |
| 结算单 | SettlementStatement | 对账周期内提交确认的一组费用项 | 单次试算结果 |
| 结算行 | StatementLine | 精确引用 ChargeItem 或 Adjustment 的不可变对账明细 | 可直接修改的金额字段 |
| 调整单 | Adjustment | 对既有费用或结算结果进行补差、核减或红冲的独立记录 | 修改原费用项 |
| 结算锁 | SettlementLock | 对已确认 Statement 版本和行摘要的不可变锁定 | 数据库行锁 |
| 财务交接 | FinanceHandoff | 将锁定业务应收/应付交给财务系统的幂等批次 | ServiceOS 总账 |
| 源快照 | SourceSnapshot | 迁移时旧系统某一一致性水位的不可变数据清单和摘要 | 随时变化的源表 |
| 迁移血缘 | MigrationLineage | 新对象/字段到源快照、记录和转换版本的可追溯关系 | 只有旧新 ID 对照 |
| 切换群组 | CutoverCohort | 按确定性规则划分权威系统和副作用权限的一组业务 | 临时随机流量 |
| 权威系统分配 | WorkOrderAuthorityAssignment | 指定工单当前唯一写入事实源的记录 | 双系统同时可写 |
| 副作用围栏 | SideEffectFence | 在派单、通知、回传和结算前校验环境与权威归属的强制策略 | 普通功能开关 |
| 发布门禁 | RolloutGate | 用指标、阈值和证据决定试点扩大或暂停的规则 | 仅按日期推进计划 |
| 配置包 | ConfigurationBundle | 工单锁定的一组发布版本引用 | 可变的项目当前配置 |
| 配置资产 | ConfigurationAsset | 具有稳定编码、所有者和版本生命周期的业务配置 | 任意键值参数 |
| 草稿修订 | DraftRevision | 尚未发布、可继续编辑的一次配置修订 | 生产运行版本 |
| 发布版本 | PublishedVersion | 已解析、已审批且不可变的配置内容 | 可原地修改的配置记录 |
| 配置发布 | ConfigurationRelease | 原子发布的一组相互兼容资产版本 | 单个文件上传 |
| 配置解析器 | ConfigurationResolver | 按业务上下文确定唯一发布并生成配置包的能力 | 运行时猜测最近配置 |
| 阶段实例 | StageInstance | 工单中某一业务阶段的一次运行实例 | 单一工单状态 |
| 任务定义 | TaskDefinition | 已发布流程中任务的责任、输入、动作、SLA 和失败策略 | 运行时待办 |
| 任务实例 | Task | 具有实际责任人、状态、SLA 和结果引用的执行单元 | 流程引擎内部节点 |
| Outbox | OutboxEvent | 与聚合事务一起写入、随后可靠投递的领域事件记录 | 普通应用日志 |
| Inbox | InboxRecord | 消费者按 eventId 去重并保存处理结果的可靠消费记录 | Broker ack 状态 |
| 幂等记录 | IdempotencyRecord | 按操作和请求摘要稳定返回同一业务结果的记录 | 领域业务唯一约束 |
| 事实资格锁 | FactEligibilityGuard | 事实更正与试算/结算写入共享的排他并发锁点 | 可计价布尔投影 |
| 工作单权威版本 | AuthorityVersion | 普通领域命令提交前必须匹配的唯一写入权威版本 | 入口路由缓存 |
| 模块公开接口 | Module API | 一个逻辑模块允许其他模块编译依赖的命令、查询、事件和模型 | Controller 或 internal 类 |
| 作用域谓词 | ScopePredicate | 授权引擎根据主体和策略编译出的数据库查询约束 | 查询后内存过滤 |
| Portal | Portal | 面向一类用户旅程、权限和发布责任的独立应用入口 | 按角色隐藏菜单的单一万能 App |
| 发布证据包 | Release Evidence Pack | 关联提交、镜像、迁移、配置、测试和签署的可审计发布材料 | 一张上线截图 |
| 业务能力权限 | Capability | 稳定的业务操作授权编码 | 菜单或按钮名称 |
| 数据范围策略 | DataScopePolicy | 对品牌、项目、区域、网点和工单关系的访问约束 | 任意 SQL 条件 |
| 角色授权 | RoleGrant | 主体在明确组织、时间和数据范围获得角色的记录 | 永久静态角色字段 |
| 附加义务 | Obligation | 允许动作时必须满足的二次确认、原因、审批或脱敏要求 | 单纯允许/拒绝 |
| 操作审计 | AuditRecord | 记录主体、动作、资源版本、授权依据、差异和结果的不可变记录 | 普通运行日志 |
| 网点 | ServiceNetwork | 承接现场服务的组织单位 | 师傅个人 |
| 师傅 | Technician | 实际执行现场作业的服务人员 | 系统用户账号 |

## 待定术语

- “勘安”：需确认是业务产品、组合工单，还是勘测通过后自动进入安装的流程模板；
- “买损”：需业务确认正式定义、责任认定和财务处理口径；
- “服务兵”：需确认是否等同师傅、外包工程师或特定人员类型；
- “品牌经理/品牌负责人”：当前对话中存在混用，正式角色模型需统一。
