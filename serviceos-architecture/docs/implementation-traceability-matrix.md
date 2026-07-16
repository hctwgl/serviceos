---
title: 研发模块与架构证据追踪矩阵
version: 0.1.0
status: Proposed
---

# 研发模块与架构证据追踪矩阵

## 1. 用途

本矩阵帮助研发 Issue、代码模块、数据库迁移和测试定位权威设计来源。它是索引，不复制源文档语义；发生冲突时，以产品宪法、Accepted ADR 和对应领域文档为准。

## 2. 模块追踪

| 实现模块 | 领域/架构输入 | API/事件输入 | 数据输入 | 验收输入 | 首次阶段 |
|---|---|---|---|---|---|
| identity | ARCH-07、ARCH-21 | API-01/02 通用身份上下文 | DATA-02 | M2 AUTH、M6 SEC | E1 |
| organization | ARCH-01、ARCH-07、ARCH-11 | API-02/04 | DATA-02/04 | M2 AUTH、M4 DSP | E1/E4 |
| project/workorder query | ARCH-01/03/05、M64～M68 | API-02/07、OpenAPI Core 0.39.0、project.created@v3、project.scope-relations-revised@v1 | DATA-01、V064～V068 | M2 CFG/WO、M7 ADM、M64～M68 | E0/E2 |
| authorization | ARCH-07、ARCH-21、M63～M67 | API-01/02、ProjectScopeAuthorizationService、ProjectRegionScopeResolver、ProjectNetworkScopeResolver | DATA-02、V064～V067 | M2 AUTH、M6 SEC、M63～M67 | E1/E4 |
| audit | ARCH-07、ARCH-21 | 所有高风险命令 | DATA-02 | M2 AUD、M6 SEC/OPS | E1 |
| authority | ARCH-17、ARCH-20 | API-01/05 authority/fence | DATA-05 | M5 CUT、M6 TX | E1/E5 |
| configuration | ARCH-05 | API-01/02 | DATA-01 | M2 CFG | E2 |
| files | ARCH-10、ARCH-21、ARCH-25 | API-03 资料引用、API-08 文件控制面 | DATA-03、V010 物理迁移 | M3 FILE、M6 SEC、M11 | E1/E3 |
| reliability | ARCH-20、ADR-014 | API-01 通用命令/事件 | DATA-01 | M6 TX | E1 |
| readmodel | PRODUCT-01～07、ARCH-19 | API-06 | DATA-06 | M7 WO/QRY | U0/U1 |
| automation | ARCH-06、ARCH-20 | API-01 事件 | DATA-01 | M2 TASK、M6 TX | E1 |
| operations | ARCH-14、ARCH-20、M60 | API-04 exception、outbound-delivery-recovered@v1、operational-exception-resolved@v2 | DATA-04、V060 | M4 OPS、M6 TX、M60 | E1/E4 |
| workorder | ARCH-03/06 | API-01/02 | DATA-01 | M2 WO | E2 |
| task | ARCH-06、M61 | API-01/02、task.created/completed@v1/v2 | DATA-01、V061 | M2 TASK、M61 | E1/E2 |
| workflow | ARCH-06/20、ADR-006 | API-01 领域事件 | DATA-01 process link | M2 WF、M6 TX-011 | E2 |
| appointment | ARCH-08 | API-03 | DATA-03 | M3 APT | E3 |
| fieldwork | ARCH-08 | API-03 | DATA-03 | M3 VISIT/FIELD | E3 |
| forms | ARCH-09、ADR-018/022 | API-03、form.submitted@v1 | DATA-03、V053 | M3 FORM、M53 FRM | E3 |
| evidence | ARCH-10、ADR-008/018/022 | API-03、evidence.slots-reresolved@v1 | DATA-03、V053 | M3 EVD/FILE、M53 | E3 |
| review | ARCH-10 | API-03、OpenAPI 0.30.0、client-review-case-created@v1 | DATA-03、V049/V054/V056 | M3 REV/COR、M55/M57 | E3 |
| network | ARCH-11 | API-04 | DATA-04 | M4 NET | E4 |
| dispatch | ARCH-11、ADR-009 | API-04 | DATA-04 | M4 DSP/ASN | E4 |
| sla | ARCH-12、M61～M66 | sla.started/breached/met@v1；API-04、OpenAPI Core 0.38.0 | DATA-04、V061～V066 | M4 SLA、M61～M66 | E4 |
| integration | ARCH-13、ADR-010/014、M57～M60 | API-04、OpenAPI Core 0.32.0、BYD CPIM 0.3.0、outbound-delivery-created/acknowledged/replay-requested/recovered@v1、route/callback 事件 | DATA-04、V055～V060 | M4 INT/DLV、M56～M60 | E2/E4 |
| notification | ARCH-14 | API-04 | DATA-04 | M4 NTF | E4 |
| facts | ARCH-04/15 | API-05 | DATA-05 | M5 FACT | E5 |
| pricing | ARCH-04/15、ADR-011 | API-05 | DATA-05 | M5 CALC | E5 |
| settlement | ARCH-16、ADR-004/011 | API-05（feature-gated） | DATA-05 | FORMAL_SETTLEMENT | 二期 |
| migration | ARCH-17、ADR-012 | API-05 | DATA-05 | M5 MIG | E5 |
| rollout | ARCH-17/18、ADR-012 | API-05 | DATA-05 | M5 CUT、M6 OPS | E5/E6 |

## 3. 不变量追踪

| 不变量 | 运行时执行点 | 数据约束 | 自动化证据 |
|---|---|---|---|
| 发布配置不可变 | Configuration publish/use case | version+digest、禁止 UPDATE published content | M2 CFG |
| 工单锁定配置包 | CreateWorkOrder | configuration_bundle_id/version 非空 | M2 WO |
| Task 是执行责任源 | Task command/assignment | active assignment 排他、aggregateVersion | M2 TASK、M4 ASN |
| 流程不改业务表 | Workflow adapter | 仅 process link/correlation inbox | M2 WF、M6 TX-011 |
| 资料版本不可覆盖 | Evidence finalize/submit | revision append-only、snapshot member 固定 | M3 EVD |
| 审核决定不可覆盖 | Review command | decision version append-only | M3 REV/COR |
| 改派责任原子切换 | Assignment saga | ACTIVE 排他、capacity reservation、task guard | M4 ASN |
| 业务重试唯一调度 | Task executor | Task.nextRunAt 是调度事实，Attempt 记录本次选定值；Delivery 不拥有重试钟 | M4 DLV/NTF、M6 TX |
| 对上/对下隔离 | Pricing context resolver | direction-specific run/items | M5 CALC |
| 缺失不等于零 | Fact/Pricing validation | typed fact state | M5 FACT/CALC |
| 事实更正不穿透结算 | CorrectFact/eligibility/collect | FactEligibilityGuard + run hold | M5 FACT-004A/B |
| SHADOW 永不可结算 | Pricing/Settlement gate | mode + pricingAuthorityVersion + feature | M5 CALC-006/010、SET-009 |
| 一张工单单一写权威 | 所有领域命令 | AuthorityAssignment ACTIVE/version | M5 CUT-002/004、M6 TX-010 |
| 副作用最终门禁 | Delivery/Notification/Assignment/Settlement | fence decision + authorityVersion | M5 CUT-011/012 |
| 同一费用不重复结算 | Settlement collect/line | lineBusinessKey 排他、source XOR | FORMAL_SETTLEMENT SET |
| 数据迁移可追溯 | Migration batch/import API | SourceSnapshot/Lineage/IdMapping | M5 MIG |
| 消息至少一次但结果一次 | Outbox/Inbox/consumer | eventId/digest unique | M6 TX-004/005/006 |
| 跨租户不可见 | Query/Command authorization | tenant + ScopePredicate | M2 AUTH、M6 SEC |

## 4. Issue 模板中的必需引用

```markdown
Architecture: ARCH-xx §n
Decision: ADR-xxx
API/Event: API-xx / Command / Event
Data owner: module + entity
Business sample: M1-xx / SAMPLE-xxx
Acceptance: scenario IDs
Security classification: INTERNAL/CONFIDENTIAL/RESTRICTED
Feature gate/authority: if applicable
```

缺少引用时，Issue 只能作为 discovery/spike，不能直接成为生产业务实现任务。

## 5. 发布 Coverage Report

每次发布按以下维度输出“已验证/未验证/不适用”：

- 业务产品与流程版本；
- 项目、品牌、区域和 cohort；
- Admin/Network/Technician Portal；
- 正常、异常、恢复和权限场景 ID；
- 外部连接器和 sandbox/production 模式；
- 配置包、数据迁移和价格模式；
- SLO/备份/回退证据；
- 已知风险与债务。

存在通用代码或某个样例通过，不得推断所有服务类型、项目或异常已经闭环。

## 6. 当前参考实现证据

| 里程碑 | 代码/迁移 | 自动化测试 | 仍未证明 |
|---|---|---|---|
| M8 | Project、Audit、Idempotency、Outbox 事务切片 | Modulith、Domain、Contract、PostgreSQL IT | 完整 E1/履约链路 |
| M9 | OIDC principal、tenant/capability 授权、Inbox、Outbox worker/attempt | Identity/Auth/Web MVC/Worker Unit + PostgreSQL IT | RoleGrant/ScopePredicate/FieldPolicy、正式 IdP/Broker、Outbox DEAD 异常 Task |
| M10 | 自动 Task、ExecutionAttempt、claim/lease、重试时钟、OperationalException、HUMAN handling Task | Task worker Unit + PostgreSQL IT + Event Schema | automation 模块提取、正式 Broker 路由、通用 Task 动作/负责人、authority fence、运行指标 |
| M11 | UploadSession、私有短期传输、Finalize 校验、StoredFile 隔离、扫描 Task、授权下载 | File Unit/Web/Contract + PostgreSQL IT + Event Schema | 正式对象存储/反病毒、multipart/清理、EvidenceSlot 关系、OCR/质量/保留销毁、真实环境 SLO |
| M12 | Git 基线 OpenAPI diff、事件版本不可变门禁、固定 TypeScript 客户端生成与来源清单 | Parser/Schema/Generator JUnit + Shell 正负向门禁 + 生成树重现性 + 根仓库 22 个 PostgreSQL IT | Portal 消费/编译、npm 发布签名/SBOM、多语言 SDK、远端 workflow 绿色结果 |
| M13 | correlation 上下文、W3C Outbox Trace、探针、Prometheus 指标、ECS JSON 脱敏、本地可观测性栈 | Unit/Web + PostgreSQL IT + in-memory Span + Shell 泄露门禁 + Compose/组件 smoke | 生产 HA/告警/容量、集中日志、跨服务完整 Trace、远端 workflow 绿色结果 |
| M14 | 单一非 root OCI 镜像、独立 Flyway 迁移、runtime 最小数据库权限、失败关闭发布、应用回滚/恢复 | Docker build/inspect + PostgreSQL 18 空库迁移 + HTTP/security smoke + rollback/negative rehearsal | 正式 registry/SBOM/签名、真实 orchestrator 滚动、PITR/对象恢复、容量、生产审批、远端 workflow 绿色结果 |
| M15 | tenant/project/region/network RoleGrant 解析、scope 解释、FieldPolicy 缺省隐藏与显式拒绝优先 | Authorization Unit + PostgreSQL IT | organization/relation scope、读模型/导出/文件全入口接线、配置发布 UI |
| M16 | Published Asset/Bundle 最小发布解析、tenant/project/bundle 工单锁定、BYD CPIM 入站创建工单 | Modulith + Configuration/WorkOrder/BYD HTTP PostgreSQL IT + V014 fail-closed migration | Stage/Task/Workflow、Audit/Outbox、完整配置审批、真实 CPIM sandbox 与全勘安链路 |
| M17 | WorkOrderReceived Outbox、Inbox 幂等、精确 Workflow 启动、首 Stage/Task 与 WorkOrder 激活 | Modulith + Parser/Dispatcher Unit + Event Schema + WorkflowBootstrap PostgreSQL IT + V015～V017 | 后续节点推进、网关/并行/等待、负责人/SLA 绑定、正式 Broker、完整勘安链路 |
| M18 | TaskCompleted 领域事件、冻结定义解析、NodeInstance 与同阶段唯一无条件下一任务推进 | Parser/Dispatcher Unit + Event Schema + WorkflowLinearProgression PostgreSQL IT + V018 | 跨阶段/END、网关/并行/等待、人工任务动作、负责人/SLA、正式 Broker、完整勘安链路 |
| M19 | 唯一无条件跨阶段、END、Stage/Workflow 完结与 WorkOrder FULFILLED | Parser/Dispatcher Unit + Event Schema + WorkflowLinearProgression PostgreSQL IT + V019 | 网关/并行/等待、人工任务动作、负责人/SLA、取消/重开、正式 Broker、完整勘安链路 |
| M20 | 人工工作流 Task claim/start/complete、HTTP、授权审计、幂等冻结响应 | MVC Security + Event/OpenAPI Contract + HumanTask/Workflow PostgreSQL IT + V020 | Assignment/候选人、release/block/cancel、SLA、离线同步、动态表单完成条件、完整勘安链路 |
| M21 | USER 候选快照、唯一 ACTIVE RESPONSIBLE、claim 门禁与 release/reclaim | MVC Security + Event/OpenAPI Contract + TaskAssignment/HumanTask/Workflow PostgreSQL IT + V021 | 策略解析、ServiceAssignment/容量/改派 saga、Task guard、SLA、离线撤权、完整勘安链路 |
| M22 | REASSIGNMENT TaskExecutionGuard、精确解除、人工命令与候选替换失败关闭 | Event Schema + TaskExecutionGuard/TaskAssignment/HumanTask PostgreSQL IT + V022 | PREPARED TaskAssignment、ServiceAssignment/容量/改派 saga、超时补偿、SLA、离线撤权、完整勘安链路 |
| M23 | guard + PREPARED 责任原子准备、激活切换、切换前 abort | Event Schema + TaskReassignment/HumanTask PostgreSQL IT + V023 | Dispatch/ServiceAssignment/容量、跨模块 Inbox saga、切换后补偿、SLA、完整勘安链路 |
| M24 | ServiceAssignment、容量预留、切换前 abort 与本地激活 saga | Event Schema + DispatchServiceAssignment PostgreSQL IT + V024 | 跨模块 Inbox saga、派单策略、切换后补偿、SLA、完整勘安链路 |
| M25 | Dispatch/Task 四段 Inbox 改派握手、实时授权复核、失败后向前重试 | Event Schema + DispatchTaskReassignmentSaga PostgreSQL IT + V025 | 初派握手、Network 双级派单、策略、自动 abort/补偿、SLA、完整勘安链路 |
| M26 | TASK_PREPARED 持久检查点、切换前 ABORTING/ABORTED 可靠终止、过期 checkpoint 抑制 | Event Schema + DispatchTaskReassignmentSaga PostgreSQL IT + V026 | 超时自动决策、初派握手、Network 双级派单、策略、切换后补偿、SLA、完整勘安链路 |
| M27 | 激活逐阶段 deadline、并发安全超时 occurrence、可靠异常事件、去重 OperationalException 与 HUMAN handling Task | Event Schema + DispatchTaskReassignmentSaga/TaskExecution PostgreSQL IT + V027 | 超时后的策略化 abort/继续/补偿、异常自动恢复关单、初派握手、Network 双级派单、候选策略、SLA、完整勘安链路 |
| M28 | 激活完成事件驱动异常自动解决、Task 精确取消/撤权、恢复证据约束与解决事件 | Handler Unit + Event Schema + TaskExecution PostgreSQL IT + V028 | 超时后的策略化 abort/继续/补偿、初派握手、Network 双级派单、候选策略、SLA、完整勘安链路 |
| M29 | 租户隔离运营异常列表/详情、动态筛选、稳定游标、OPEN 确认与来源自动解决兼容 | MVC Security + OpenAPI/Event Contract + OperationalExceptionWorkbench PostgreSQL IT + V029 | 领域专属处置动作、通用处理任务编排、通知升级、完整运营工作台前端 |
| M30 | 预约提议/确认/改约、不可变修订链、责任快照、实时 scope 授权与 ETag 并发控制 | MVC Security + OpenAPI/Event Contract + Appointment PostgreSQL IT + V030 | 取消/爽约、上门签到、现场执行、离线同步、完整勘安链路 |
| M31 | 联系尝试追加历史、首次联系 SLA 事实、预约取消/爽约终态、终态修订与可靠通知 | MVC Security + OpenAPI/Event Contract + Appointment PostgreSQL IT + V031 | 上门签到、Visit 现场执行、离线同步、下游取消/爽约任务策略 |
| M32 | Visit 签到/签退/中断、围栏 WARN/BLOCK、captured/received 双时间、重复上门序号、改派后离线撤权 | MVC Security + OpenAPI/Event Contract + Visit PostgreSQL IT + V032 | FieldOperation 结构化结果、表单/资料完整性、离线工作包有效期与人工合并 |
| M33-FOUNDATION | FORM 发布门禁、多表单 Bundle、Task `formRef`/Bundle 冻结及授权只读解析 | OpenAPI 0.9.0 + Configuration/Workflow/Task PostgreSQL IT + V033～V035 | SERVICEOS_EXPR_V1、草稿、预填冲突和 M3 FORM-001～005 |
| M34 | 精确 FormVersion 不可变提交、固定 required/基础类型验证、责任/guard 门禁、幂等审计事件闭环 | OpenAPI 0.10.0 + FormSubmission PostgreSQL/MVC/Event Contract + V036 | ADR-018 表达式/validator、草稿、预填冲突、更正审核和完整 FORM-001～005 |
| M35 | 表单 Task 完成仅接受同 Task/Project/冻结 FormVersion 的 VALIDATED submission 与 contentDigest | OpenAPI 0.11.0 + FormSubmission/HumanTask PostgreSQL IT + Modulith | EvidenceSet/审核完成条件、整改 supersede、领域映射和完整 FORM-001～005 |
| M36 | EVIDENCE Schema 发布门禁、身份/数量语义校验、多模板 Bundle 锁定与漂移保护 | Configuration PostgreSQL IT + Schema Drift Test | ADR-008/018、EvidenceSlot/Revision/Snapshot、审核整改和 EVD-001～009 |
| M37 | Task 冻结 Stage、固定 EvidenceSlot 可靠解析、权威空结果与授权只读投影 | OpenAPI 0.12.0 + Evidence PostgreSQL/MVC/Event Contract + V037～V038 | ADR-018 条件重解析、EvidenceSetSnapshot、完成门禁、审核整改和完整 EVD-001～009 |
| M38 | Evidence 编排安全文件 Begin/Finalize、EvidenceItem/不可变 Revision、扫描状态投影与授权查询 | OpenAPI 0.13.0 + Evidence PostgreSQL/MVC/Event Contract + V039 | OCR/图像业务校验实现、代办上传、invalidate、Snapshot、审核整改、完成门禁和完整 EVD-001～009 |
| M39 | 扫描后确定性机器校验、EvidenceValidation 事实、VALIDATED/VALIDATION_FAILED | OpenAPI 0.14.0 + Evidence PostgreSQL/Event Contract + V040 | OCR/图像 CV 实现、GPS 权威距离、invalidate、Snapshot、审核整改、完成门禁 |
| M40 | 不可变 EvidenceSetSnapshot、TASK_SUBMISSION 成员资格与 EVD-008/009 | OpenAPI 0.15.0 + Evidence PostgreSQL/MVC/Event Contract + V041 | REVIEW/REPORT purpose、自动选版、invalidate、Review/Correction、完成门禁 |
| M41 | 无 formRef 资料 Task 完成仅接受精确 EvidenceSetSnapshot 引用与 digest | OpenAPI 0.16.0 + Evidence/HumanTask PostgreSQL IT + Modulith | Review/Correction |
| M42 | 授权 VALIDATED→INVALIDATED、槽位投影刷新、历史 Snapshot 不可改写 | OpenAPI 0.17.0 + Evidence PostgreSQL/MVC/Event Contract + V042 | files 文件作废联动、Review/Correction、双引用完成 |
| M43 | formRef 且非空 EvidenceSlot 的 HUMAN Task 仅接受精确 FormSubmission + TASK_SUBMISSION Snapshot 双引用，并在同一完成事务冻结 inputVersionRefs | OpenAPI 0.18.0 + HumanTask/Evidence/Form PostgreSQL IT + V043 | files 文件作废联动、Review/Correction、条件槽位重解析 |
| M44 | ReviewCase 绑定 TASK_SUBMISSION Snapshot；只追加 APPROVED/REJECTED 决定 | OpenAPI 0.19.0 + Evidence PostgreSQL/MVC/Event Contract + V044 | CorrectionCase、强制通过、重开、车企回执 |
| M45 | REJECTED 同事务创建 CorrectionCase；补传轮次只追加；RESUBMITTED→CLOSED | OpenAPI 0.20.0 + Evidence PostgreSQL/MVC/Event Contract + V045 | 整改 Task、IN_PROGRESS/WAIVED、强制通过、重开、车企回执 |
| M46 | Evidence invalidate 同事务联动 StoredFile AVAILABLE→INVALIDATED；禁止下载授权 | OpenAPI 0.21.0 + Files/Evidence PostgreSQL IT + V046 | 物理删除、Retention、对象存储厂商切换 |
| M47 | CorrectionCase 打开时自动创建 evidence.correction HUMAN Task；IN_PROGRESS 投影 | OpenAPI 0.22.0 + Evidence PostgreSQL IT + V047 | 自动指派、WAIVED、强制通过、重开、条件槽位 |
| M48 | OPEN 强制通过 FORCE_APPROVED；APPROVED/FORCE_APPROVED 重开新 OPEN 案例 | OpenAPI 0.23.0 + Evidence PostgreSQL/MVC/Event Contract + V048 | 车企回执、二级审批/MFA、条件槽位 |
| M49 | 适配层记录 ExternalReviewReceipt；追加 EXTERNAL 决定；驳回开客服协调 Task | OpenAPI 0.24.0 + Evidence PostgreSQL/MVC/Event Contract + V049 | Connector 入站表、CLIENT Case 自动创建、affectedTargets 强校验 |
| M50 | 整改 Task 自动写入源 Evidence Task RESPONSIBLE 为 SYSTEM CANDIDATE | V050 + CorrectionCase PostgreSQL IT | 多候选人策略、自动 claim、条件槽位 |
| M51 | CorrectionCase 高风险豁免进入 WAIVED；取消整改 Task | OpenAPI 0.25.0 + Evidence PostgreSQL/MVC/Event Contract + V051 | 条件槽位、OCR/CV、多候选人策略、自动 claim |
| M52 | SERVICEOS_EXPR_V1 条件 EvidenceSlot；true/false 决策与输入摘要不可变审计；复杂度失败关闭 | ADR-018 + Expression/Modulith Unit + Evidence PostgreSQL IT + V052 | 表单条件求值、字段变化重解析、决策表/公式/脚本、OCR/CV |
| M53 | VALIDATED 表单事实驱动只追加 Evidence resolution generation；槽位世代/lineage；REVIEW_REQUIRED 与显式 KEEP/INVALIDATE | ADR-022 + OpenAPI 0.26.0 + Expression/Form/Evidence/MVC/Contract/PostgreSQL IT + V053 | 计算字段、决策表/脚本、草稿离线合并、OCR/CV、GPS 权威距离 |
| M54 | ExternalReviewReceipt 目标以 slot/item/revision 三元组精确命中 ReviewCase 冻结 SnapshotMember；跨 Snapshot、错配和重复失败关闭 | ARCH-10 + OpenAPI 0.27.0 + ReviewCase PostgreSQL IT + MVC Security + Contract/Client Generation + ArchitectureTest；无新迁移 | 完整 Connector 入站、CLIENT Case 自动创建、外部批次权威登记、其他 targetType 与自动整改映射 |
| M55 | INTERNAL/CLIENT ReviewCase 来源分离；已通过总部审核后显式登记 CLIENT Case；回执批次与 mapping 匹配冻结值 | ARCH-10 + OpenAPI 0.28.0 + `client-review-case-created@v1` + V054 + ReviewCase PostgreSQL/MVC/Contract/Client/ArchitectureTest | Connector 验签与通用入站、OutboundDelivery、交付成功事件自动创建、integration 域批次权威登记、其他 targetType |
| M56 | BYD 创建工单验签后登记不可变 Envelope/Canonical；私有原文；transport/业务键幂等；崩溃恢复；授权摘要查询 | ARCH-13 + OpenAPI 0.29.0 + `integration.canonical-message-processed@v1` + V055 + BYD/Replay PostgreSQL IT + Object Storage/Query/Security/Contract/Client/ArchitectureTest | 其他 CPIM messageType、外部审核回调标准化、OutboundDelivery、网络 Connector、自动重试/重放、生产对象存储和 Portal |
| M57 | BYD 厂端审核回调按显式订单路由拆分 Canonical/item；M49 外部决定；部分成功、transport/业务幂等与故障恢复 | ARCH-10/13 + OpenAPI Core 0.30.0/BYD 0.2.0 + route/callback 事件 Schema + V056/V057 + ReviewCase/Signature/Mapper/Security/Contract/Client/ArchitectureTest | OutboundDelivery、自动创建 CLIENT Case、其他 CPIM 消息、自动 Evidence target 映射、生产 Connector/对象存储和 Portal |
| M58 | 已通过 INTERNAL Case 派生不可变 BYD 提审 Delivery；Attempt/Acknowledgement 分离；Task 唯一重试时钟；UNKNOWN 不重发并进入人工异常；成功自动创建 CLIENT Case/Route | ARCH-13 + ADR-010/014 的 M58 已批准子集 + OpenAPI Core 0.31.0/BYD 0.3.0 + outbound delivery 事件/外部 Schema + V058 + ReviewCase/Gateway/Security/PostgreSQL/Contract/Client/ArchitectureTest | UNKNOWN 人工处置命令、其他 CPIM 消息、通用 Connector、生产凭据/对象存储/sandbox、自动 Evidence target 映射和 Portal |
| M59 | UNKNOWN Delivery 经 USER/HIGH capability、原因、审批引用和预期版本授权人工重发；复用冻结 payload/external key；ReplayRequest/Task/Audit/Outbox 原子登记；旧 UNKNOWN Attempt 保留 | ARCH-13 + ADR-010/014 的 M59 已批准子集 + OpenAPI Core 0.32.0 + replay-requested@v1 + V059 + ReviewCase/MVC/PostgreSQL/Contract/Client/ArchitectureTest | M59 当时未实现异常自动闭环（后由 M60 补齐）；人工标记已送达/放弃、远端查询、批量审批、其他 CPIM、通用 Connector、生产基础设施和 Portal仍未实现 |
| M60 | M59 重发取得严格 ACK 后发布恢复事实；Operations 幂等关闭同 Delivery 历次 UNKNOWN Task 异常；恢复先到时以不可变 marker 抑制迟到失败 HUMAN Task | M28/M58/M59 + ADR-010/014 的 M60 已批准子集 + recovered@v1/resolved@v2 + V060 + ReviewCase/TaskExecution/Handler/Contract/PostgreSQL/ArchitectureTest | 人工标记已送达/放弃、远端查询、完整通知、批量审批、其他 CPIM、通用 Connector、生产基础设施和 Portal |
| M61 | Workflow 显式 `slaRef` 精确命中同 Bundle SLA v1；Task 创建/完成驱动 ELAPSED 时钟；到期对账形成 BREACHED，按时/逾期完成形成 MET/MET_LATE 并保留超时历史 | ARCH-12 的 M61 确定性子集 + SLA config schema v1 + started/breached/met@v1 + V061 + Configuration/SLA PostgreSQL IT + Contract/Event Governance/ArchitectureTest | BUSINESS 日历、暂停/恢复、免责/重算、预警/升级/通知、其他 subject、SLA HTTP/Portal、考核结算 |
| M62 | `sla.read` + 实时 Project Scope 暴露项目 SLA 工作台、工单时间线和实例/segment/milestone 详情；服务端 asOf 计算动态秒数；游标绑定查询范围 | ARCH-12 + API-04 + OpenAPI Core 0.33.0 + V062 + SLA PostgreSQL/MVC/Contract/Client/ArchitectureTest | BUSINESS 日历、暂停/恢复、免责/重算、预警/升级/通知、跨项目范围投影、Portal 前端、考核结算 |
| M63 | 实时 TENANT/PROJECT RoleGrant 解析为授权项目集合；省略 projectId 的 SLA 队列以单条范围化 SQL 查询；授权集合摘要绑定游标；REGION/NETWORK 无映射时拒绝审计 | ARCH-07/12 + API-04 + OpenAPI Core 0.34.0 + V063 + Authorization/SLA PostgreSQL/MVC/Contract/Client/ArchitectureTest | REGION/NETWORK/组织关系投影、授权缓存/导出、BUSINESS 日历、暂停/预警/通知、Portal、考核结算 |
| M64 | Project 创建原子写入有效期 REGION 关系；REGION RoleGrant 经公开解析端口形成精确项目集合并复用跨项目 SLA 队列；关系变化使游标失败关闭；NETWORK 继续拒绝 | ARCH-07/12 + API-07/API-04 + OpenAPI Core 0.35.0 + project.created@v2 + V064 + Project/Authorization/SLA PostgreSQL/MVC/Contract/Client/ArchitectureTest | NETWORK/组织关系、Region 层级后代、关系修订命令、授权缓存/导出、BUSINESS 日历、暂停/预警/通知、Portal、考核结算 |
| M65 | Project 创建原子写入有效期 NETWORK 关系；NETWORK RoleGrant 经公开解析端口形成精确项目集合并复用跨项目 SLA 队列；关系变化使游标失败关闭 | ARCH-07/11/12 + API-07/API-04 + OpenAPI Core 0.36.0 + project.created@v3 + V065 + Project/Authorization/SLA PostgreSQL/MVC/Contract/Client/ArchitectureTest | ServiceNetwork 生命周期/覆盖/能力/停派、组织层级、关系修订、授权缓存/导出、派单策略、BUSINESS SLA、Portal、结算 |
| M66 | Project REGION/NETWORK 当前关系以显式完整集合即时修订；结束/追加历史、Project 版本、不可变收据、审计、Outbox 与幂等同事务；授权映射即时变化且旧 SLA 游标失败关闭 | ARCH-07/11/12 + API-07/API-04 + OpenAPI Core 0.37.0 + project.scope-relations-revised@v1 + V066 + Project/Authorization/SLA PostgreSQL/MVC/Contract/Client/ArchitectureTest | ServiceNetwork/Region/Organization 目录与生命周期、计划生效和审批、项目生命周期、授权缓存/导出、派单策略、BUSINESS SLA、Portal、结算 |
| M67 | `project.read` 实时 TENANT/PROJECT/REGION/NETWORK 范围驱动项目目录、详情和 M66 不可变范围历史；SQL 范围收敛；cursor 绑定授权与筛选；撤权审计和跨租户 404 | ARCH-01/07 + API-07 + OpenAPI Core 0.38.0 + V067 + Project PostgreSQL/MVC/Contract/Client/ArchitectureTest | owners、品牌/服务产品/配置绑定、项目生命周期、ServiceNetwork/Region/Organization 目录、计划修订审批、导出分析、Portal、派单/BUSINESS SLA/结算 |
| M68 | `workOrder.read` 实时项目范围驱动不含客户 PII 的工单目录和详情；SQL 范围收敛；cursor 绑定授权与筛选；拒绝审计与跨租户 404 | ARCH-01/07 + API-02 + OpenAPI Core 0.39.0 + V068 + WorkOrder PostgreSQL/MVC/Contract/Client/ArchitectureTest | 客户敏感详情及增强读取审计、阶段/任务/时间线/动作、派单/SLA 风险筛选、Portal、完整生命周期 |
| M69 | 复用 M68 鉴权的 Workflow/Stage 当前投影与 Task 摘要稳定分页；未初始化显式空投影；模块内查询和敏感字段最小化 | ARCH-03/05 + API-02 + OpenAPI Core 0.40.0 + V069 + Workflow PostgreSQL/MVC/Contract/Client/ArchitectureTest | 完整时间线、Node/Attempt 历史、允许动作、Task 独立队列/详情、客户 PII、Portal |
| M70 | `task.read` 实时 TENANT/PROJECT/REGION/NETWORK 范围驱动独立 Task 队列与详情；active assignment 精确筛选；cursor 绑定范围与筛选；详情冻结引用和责任事实最小披露 | ARCH-05/07 + API-02 + OpenAPI Core 0.41.0 + V070 + Task PostgreSQL/MVC/Contract/Client/ArchitectureTest | 动态允许动作、Node/Attempt 历史、SLA 聚合、客户 PII、Portal、默认候选人推断 |
| M71 | Task 详情读取边界后按与写命令相同的 capability、状态、ACTIVE CANDIDATE/RESPONSIBLE 和 execution guard 实时投影 claim/start/complete/release；版本与输入 schema/obligation 显式返回 | ARCH-05/07 + API-02 + OpenAPI Core 0.42.0 + Task PostgreSQL/MVC/Contract/Client/ArchitectureTest；无新迁移，保持 V070/72 | block/retry/cancel/manual-complete、完成条件预演、Node/Attempt 历史、SLA 聚合、Portal |
| M72 | 每页复用 M70 `task.read` 边界，按 attemptNo DESC 安全查询自动 Task 执行 Attempt；cursor 绑定 Task，HUMAN 明确空页，排除 worker/payload/错误正文 | ARCH-05/07 + API-02 + OpenAPI Core 0.43.0 + Task PostgreSQL/MVC/Contract/Client/ArchitectureTest；复用 V008 索引，无新迁移，保持 V070/72 | HUMAN 命令时间线、Workflow Node 历史、跨模块工单时间线、Attempt 写命令、SLA 聚合、Portal |
| M73 | 独立 readmodel 通过 Inbox 可靠消费 WorkOrder/Workflow/Stage/Task 核心事件，按业务时间形成可重建工单时间线；查询复用 workOrder.read，游标绑定工单并显式 UNKNOWN freshness | ARCH-05/19 + API-02/API-06 + DATA-01/06 + OpenAPI Core 0.44.0 + V071 + Readmodel PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest | Appointment/Visit/Evidence/Delivery/SLA/异常/试算/结算合并、correlation 展开、重建作业、Broker checkpoint、搜索/导出、Portal |
| M74 | 同一 readmodel Inbox 合并已发布 ContactAttempt/Appointment/Visit 事件；不保存 partyRef/GPS/自由文本；V072 expand category；查询授权与 UNKNOWN freshness 不变 | ARCH-08/19 + API-02/03/06 + DATA-03/06 + OpenAPI Core 0.45.0 + V072 + Readmodel PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest | Evidence/Review/Delivery/SLA/异常/试算合并、correlation 展开、重建作业、Broker checkpoint、搜索/导出、Portal |
| M75 | 同一 readmodel Inbox 合并 sla.started/breached/met；breached/met 经 TaskTimelineContextQuery；V073 + OpenAPI 0.46.0 x-extensible-enum | ARCH-12/19 + API-02/04/06 + DATA-06 + OpenAPI Core 0.46.0 + V073 + Readmodel PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest | Evidence/Review/Delivery/异常/试算合并、BUSINESS 日历与暂停/预警、checkpoint/重建、Portal |
| M76 | 同一 readmodel Inbox 合并 form.submitted 与 evidence snapshot/review/correction 生命周期事件；经 TaskTimelineContextQuery；V074 + OpenAPI 0.47.0 | ARCH-09/10/19 + API-02/06 + DATA-06 + OpenAPI Core 0.47.0 + V074 + Readmodel PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest | revision 技术噪声、Delivery/异常/试算、checkpoint/重建、Portal |
| M77 | outbound-delivery-created 直接投影；exception.resolved@v2 经 Task 上下文；V075 + OpenAPI 0.48.0 | ARCH-13/14/19 + API-02/04/06 + DATA-06 + OpenAPI Core 0.48.0 + V075 + Readmodel PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest | delivery ack/recovered、exception ack、试算、checkpoint/重建、Portal |
| M78 | DeliveryTimelineContextQuery + acknowledged/recovered/replay-requested 时间线；readmodel 依赖 integration::api；OpenAPI 0.49.0 | ARCH-13/19 + API-02/04/06 + DATA-06 + OpenAPI Core 0.49.0 + Readmodel/Integration PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest/L3 | checkpoint/重建、试算/结算、Portal |
| M79 | ExceptionTimelineContextQuery + exception.acknowledged 时间线；readmodel 依赖 operations::api；OpenAPI 0.50.0 | ARCH-13/19 + API-02/04/06 + DATA-06 + OpenAPI Core 0.50.0 + Readmodel/Operations PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest/L3 | checkpoint/重建、试算/结算、Portal |
| M80 | ServiceAssignment 激活生命周期并入工单时间线；V076 ASSIGNMENT；OpenAPI 0.51.0；不投影 assignee/capacity/guard | ARCH-06/19 + API-02/06 + DATA-06 + OpenAPI Core 0.51.0 + V076 + Readmodel PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest/L3 | ServiceNetwork 生命周期、试算/结算、checkpoint/重建、Portal |
| M81 | Task assigned/assignment/guard/manual-intervention 时间线；OpenAPI 0.52.0；无新 Flyway | ARCH-05/19 + API-02/06 + DATA-06 + OpenAPI Core 0.52.0 + Readmodel PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest/L3 | evidence revision 噪声、checkpoint/重建、试算/结算、Portal |
| M82 | ReviewTimelineContextQuery + external-review-receipt-recorded 时间线；readmodel 依赖 evidence::api；OpenAPI 0.53.0 | ARCH-10/19 + API-02/06 + DATA-06 + OpenAPI Core 0.53.0 + Readmodel/Evidence PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest/L3 | revision/slots 噪声、checkpoint/重建、试算/结算、Portal |
| M83 | condition-disposition-recorded 时间线（KEEP/INVALIDATE）；OpenAPI 0.54.0；无新 Flyway | ARCH-10/19 + ADR-022 + API-02/06 + DATA-06 + OpenAPI Core 0.54.0 + Readmodel PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest/L3 | revision/slots 噪声、checkpoint/重建、试算/结算、Portal |
| M84 | 时间线 checkpoint/dead letter/generation 重建；freshness FRESH/LAGGING/UNKNOWN/REBUILDING；OpenAPI 0.55.0；V077 | ARCH-19 + API-02/06 + DATA-06 Accepted 窄切片 + OpenAPI Core 0.55.0 + V077 + Readmodel/Reliability PostgreSQL/Contract/Client/ArchitectureTest/L3 | 工作区/队列/SavedView/搜索、多投影平台、Broker offset、试算/结算、Portal |
| M85 | 工单工作区顶层实时组合；缺权 SLA/异常降级；无 PII；OpenAPI 0.56.0；无新 Flyway | ARCH-19 + API-06 Accepted 窄切片 + OpenAPI Core 0.56.0 + Readmodel PostgreSQL/MVC/Contract/Client/ArchitectureTest/L3 | sections 按需加载、队列/SavedView/搜索、Portal、工作区持久化投影、试算/结算 |
| M86 | 时间线 projection_definition、dead letter 幂等重放、旧/孤儿 generation 清理；V078；OpenAPI 保持 0.56.0 | ARCH-19 + DATA-06 Accepted 窄切片 + V078 + Readmodel/Reliability PostgreSQL/ArchitectureTest/L3 | Admin 重建/重放 HTTP、多投影平台、Broker offset、队列/SavedView/Portal |
| M87 | 工作区 sections TASKS/TIMELINE_AUDIT 按需加载；OpenAPI 0.57.0；无新 Flyway | ARCH-19 + API-06 Accepted 窄切片 + OpenAPI Core 0.57.0 + Readmodel PostgreSQL/MVC/Contract/Client/ArchitectureTest/L3 | 其余 section、队列/SavedView、Portal、区块持久化投影 |
| M88 | 工作区 section APPOINTMENTS_VISITS；visit/appointment 缺权降级；无 GPS/地址/note；OpenAPI 0.58.0；无新 Flyway | ARCH-19 + API-06 Accepted 窄切片 + OpenAPI Core 0.58.0 + Readmodel PostgreSQL/MVC/Contract/Client/ArchitectureTest/L3 | 其余 section、contact-attempts、队列/SavedView、Portal、区块持久化投影 |
| M89 | 工作区 section FORMS_EVIDENCE；form/evidence 缺权降级；无 definition/资料 JSON；OpenAPI 0.59.0；无新 Flyway | ARCH-19 + API-06 Accepted 窄切片 + OpenAPI Core 0.59.0 + Readmodel PostgreSQL/MVC/Contract/Client/ArchitectureTest/L3 | 其余 section、FormSubmission 列表、EvidenceItem 明细、队列/SavedView、Portal |
| M90 | 工作区 section REVIEWS_CORRECTIONS；evidence.read + Project Scope；无审核/整改自由文本；OpenAPI 0.60.0；无新 Flyway | ARCH-10/19 + API-06 Accepted 窄切片 + OpenAPI Core 0.60.0 + Evidence/Readmodel PostgreSQL/MVC/Contract/Client/ArchitectureTest/L3 | 其余 section、审核整改命令聚合、队列/SavedView、Portal |
| M91 | 工作区 section INTEGRATION；入站 WorkOrder 结果与外发 Delivery；分离读权降级；无对象引用/operator/重放审批文本；OpenAPI 0.61.0；V079 | ARCH-13/19 + API-06 Accepted 窄切片 + OpenAPI Core 0.61.0 + V079 + Integration/Readmodel PostgreSQL/MVC/Contract/Client/ArchitectureTest/L3 | FACTS_CALCULATIONS、审核回调批次额外归属、专项队列/SavedView、Portal |
| M92 | 工作区顶层 serviceAssignmentSummary；当前 Task ACTIVE 网点/师傅责任；dispatch.read + Project Scope；OpenAPI 0.62.0；V080 | ARCH-06/19 + API-06 Accepted 顶层扩展 + OpenAPI Core 0.62.0 + V080 + Dispatch/Readmodel PostgreSQL/MVC/Contract/Client/ArchitectureTest/L3 | FACTS_CALCULATIONS、历史责任、saga/容量详情、activity-summary、队列/SavedView、Portal |
| M93 | activity-summary 复用时间线最近 N 条；无关键事件猜测、无 cursor；workOrder.read + freshness；OpenAPI 0.63.0；无新 Flyway | ARCH-19 + API-06 Accepted 窄切片 + OpenAPI Core 0.63.0 + Readmodel PostgreSQL/MVC/Contract/Client/ArchitectureTest/L3 | 关键事件 taxonomy/过滤、correlation 展开、FACTS_CALCULATIONS、队列/SavedView、Portal |
| M94 | APPOINTMENTS_VISITS 增加 ContactAttempt 安全摘要；appointment.read 降级；无联系对象/自由文本/录音/操作者；OpenAPI 0.64.0；无新 Flyway | ARCH-08/19 + API-06 Accepted 扩展 + OpenAPI Core 0.64.0 + Appointment/Readmodel PostgreSQL/MVC/Contract/Client/ArchitectureTest/L3 | 工单级联系列表端口、敏感联系详情、FACTS_CALCULATIONS、队列/SavedView、Portal |
| M95 | FORMS_EVIDENCE 增加 FormSubmission/EvidenceItem 安全元数据；独立 summary SQL；无 values/校验消息/Revision/file/captureMetadata；OpenAPI 0.65.0；无新 Flyway | ARCH-09/10/19 + API-06 Accepted 扩展 + OpenAPI Core 0.65.0 + Forms/Evidence/Readmodel PostgreSQL/MVC/Contract/Client/ArchitectureTest/L3 | 表单值与资料版本详情、跨 Task cursor、FACTS_CALCULATIONS、队列/SavedView、Portal |
| M96 | REVIEWS_CORRECTIONS 增加 INTERNAL→CLIENT 与重开血缘；复用现有 evidence API/auth；无决定/豁免文本和操作者；OpenAPI 0.66.0；无新 Flyway | ARCH-10/19 + API-06 Accepted 扩展 + OpenAPI Core 0.66.0 + Evidence/Readmodel PostgreSQL/MVC/Contract/Client/ArchitectureTest/L3 | 回调批次多工单归属、审核队列/命令聚合、FACTS_CALCULATIONS、SavedView、Portal |
| M97 | API-06 §6 review-cases 授权跨项目队列；OPEN 默认、origin/task/project 筛选、范围绑定 FIFO cursor；安全最新决定摘要；OpenAPI 0.67.0；V081 | ARCH-07/10 + API-06 Accepted 窄切片 + OpenAPI Core 0.67.0 + V081 + Evidence/Authorization PostgreSQL/MVC/Contract/Client/ArchitectureTest/L3 | 通用 work-queues/SavedView、SLA/assignee enrich、Correction/Outbound 队列、Portal |
| M98 | API-06 §6 correction-cases 授权跨项目队列；OPEN 默认、task/sourceReview/project 筛选、范围绑定 FIFO cursor；安全原因码与补传次数；OpenAPI 0.68.0；V082 | ARCH-10/58 + API-06 Accepted 窄切片 + OpenAPI Core 0.68.0 + V082 + Evidence/Authorization PostgreSQL/MVC/Contract/Client/ArchitectureTest/L3 | 通用 work-queues/SavedView、SLA/assignee enrich、Outbound 队列、异常 Scope 硬化、Portal |
| M99 | API-06 §6 outbound-deliveries 授权跨项目队列；UNKNOWN 默认、messageType/workOrder/review 筛选、范围绑定 FIFO cursor；安全摘要不含 digest/操作者/对象引用；OpenAPI 0.69.0；V083 | ARCH-13 + API-06 Accepted 窄切片 + OpenAPI Core 0.69.0 + V083 + Integration/Authorization PostgreSQL/MVC/Contract/Client/ArchitectureTest/L3 | 通用 work-queues/SavedView、异常 Scope 硬化、人工标记已送达/放弃、Portal |
| M100 | 运营异常工作台 Project Scope 硬化；projectId 筛选/响应、scopeDigest 游标；无 project 孤儿仅 TENANT 可见；OpenAPI 0.70.0；V084 | ARCH-14/42 + API-06 Accepted 窄切片 + OpenAPI Core 0.70.0 + V084 + Operations/Authorization PostgreSQL/ArchitectureTest/L3 | 通用 work-queues/SavedView、人工标记已送达/放弃、Portal |
| M101 | Admin Portal Vue+TS+Vite 只读队列外壳；审核/整改/外发/异常 Page ID 路由；本地 JWT；npm build | PRODUCT-01/02 + ARCH-19 + API-06 已实现队列消费 + Admin Web build | SavedView、工作区全页、命令 UI、OIDC SDK、Network/Technician、E2E |
| M102 | Admin 工单工作区只读页；消费 workspace/activity-summary/sections；外发队列深链；npm build | PRODUCT-01/02 + ARCH-19 + API-06 Accepted workspace 窄切片 + Admin Web build | 命令 UI、SavedView、OIDC SDK、Network/Technician、E2E |
| M103 | Admin 工作区展示当前任务 GET /tasks/{id}/allowed-actions 只读投影；不执行命令；npm build | PRODUCT-01/02 + ARCH-19 + API-02 AllowedActions + Admin Web build | 命令执行 UI、OIDC SDK、SavedView、E2E |
| M104 | Admin 授权工单目录消费 GET /work-orders；status/clientCode 筛选与工作区深链；npm build | PRODUCT-01/02 + ARCH-19 + API-02 Authorized WorkOrder Query + Admin Web build | SavedView、命令 UI、OIDC SDK、E2E |
| M105 | Admin 工作区按 allowed-actions 执行 claim/start/complete/release；Idempotency-Key+If-Match；npm build | PRODUCT-01/02/05 + ARCH-19 + API-02 Human Task Commands + Admin Web build | 表单/资料提交流程编排、OIDC SDK、SavedView、E2E |
| M106 | Admin 授权任务目录消费 GET /tasks；筛选与工作区深链；npm build | PRODUCT-01/02 + ARCH-19 + API-02 Task Directory + Admin Web build | 任务详情独立页、SavedView、OIDC、E2E |
| M107 | Admin SLA 工作台消费 GET /sla-instances；status 筛选与工作区深链；npm build | PRODUCT-01/02 + ARCH-19 + API-04/SLA Query + Admin Web build | SLA 详情操作、BUSINESS 日历、预警通知、E2E |
| M108 | Admin 授权项目目录消费 GET /projects；status/clientId 筛选；npm build | PRODUCT-01/02 + ARCH-19 + API-07 Project Directory + Admin Web build | 项目创建/范围修订 UI、配置治理、OIDC、E2E |
| M109 | Admin 任务详情消费 GET /tasks/{id} 与 execution-attempts；复用命令面板 | PRODUCT-01/02 + ARCH-19 + API-02 Task Detail/Attempts + Admin Web build | 表单/资料提交流程编排、OIDC、E2E |
| M110 | Admin 项目详情与 scope-revisions 历史 | PRODUCT-01/02 + ARCH-19 + API-07 Project Query + Admin Web build | 项目创建/范围修订命令 UI、OIDC、E2E |
| M111 | Admin 异常队列按 allowedActions 执行 ACKNOWLEDGE | PRODUCT-01/02 + ARCH-19 + API-04 Exception Acknowledge + Admin Web build | 通用 RESOLVED UI、OIDC、E2E |
| M112 | Admin 审核案例详情与 decide/force/reopen | PRODUCT-01/02 + ARCH-10/19 + Evidence Review APIs + Admin Web build | 表单/资料编排、OIDC、E2E |
| M113 | Admin 整改案例详情与 resubmit/close/waive | PRODUCT-01/02 + ARCH-10/19 + Correction APIs + Admin Web build | 资料提交流程编排、OIDC、E2E |
| M114 | Admin 外发交付详情与 UNKNOWN retry | PRODUCT-01/02 + ARCH-13/19 + Outbound APIs + Admin Web build | 人工标记已送达/放弃、OIDC、E2E |
| M115 | Admin SLA 实例详情 | PRODUCT-01/02 + ARCH-12/19 + SLA Detail API + Admin Web build | BUSINESS 日历、预警 UI、E2E |
| M116 | Admin 任务详情表单列表与 submitTaskForm；VALIDATED 回填 complete 引用 | PRODUCT-01/02 + ARCH-09/19 + Forms APIs + Admin Web build | 动态表单设计器、OIDC、E2E |
| M117 | Admin 任务详情资料槽位/项与 createEvidenceSetSnapshot；回填 complete 引用 | PRODUCT-01/02 + ARCH-10/19 + Evidence APIs + Admin Web build | Begin/Finalize 上传编排、OIDC、E2E |
| M118 | Admin 审核详情创建 BYD review submission OutboundDelivery | PRODUCT-01/02 + ARCH-13/19 + Integration submit API + Admin Web build | 其他 CPIM 提审、OIDC、E2E |
| M119 | Admin 资料 Begin→PUT→Finalize 上传编排；SHA-256 | PRODUCT-01/02 + ARCH-10/11/19 + Evidence Upload APIs + Admin Web build | 专业扫描 UI、OIDC、E2E |
| M120 | Admin REVIEW_REQUIRED 条件 KEEP/INVALIDATE 处置 | PRODUCT-01/02 + ARCH-10/19 + Condition Disposition API + Admin Web build | 自动处置策略、OIDC、E2E |
| M121 | Admin 快照后 createReviewCase 并深链 | PRODUCT-01/02 + ARCH-10/19 + Create ReviewCase API + Admin Web build | 审核队列自动刷新、OIDC、E2E |
| M122 | Admin 任务联系历史列表与追加联系事实 | PRODUCT-01/02 + ARCH-10/19 + ContactAttempt APIs + Admin Web build | 通话录音回放、OIDC、E2E |
| M123 | Admin 预约提议/确认/取消命令面板 | PRODUCT-01/02 + ARCH-10/19 + Appointment APIs + Admin Web build | 改约/爽约完整 UX、OIDC、E2E |
| M124 | Admin 预约签到与 Visit 签退模拟 | PRODUCT-01/02 + ARCH-10/19 + Visit APIs + Admin Web build | 真实设备 GPS、OIDC、E2E |
| M125 | Admin 预约改约与爽约命令 | PRODUCT-01/02 + ARCH-10/19 + Appointment reschedule/no-show APIs + Admin Web build | 日历 UX、OIDC、E2E |
| M126 | Admin Visit interrupt 命令 | PRODUCT-01/02 + ARCH-10/19 + Visit interrupt API + Admin Web build | 异常联动、OIDC、E2E |
| M127 | Admin 资料短期下载授权与 Revision 作废 | PRODUCT-01/02 + ARCH-10/11/19 + File/Evidence invalidate APIs + Admin Web build | 在线预览器、OIDC、E2E |
| M128 | Admin 项目目录 createProject | PRODUCT-01/02 + ARCH-10/19 + Create Project API + Admin Web build | 项目状态机完整 UX、OIDC、E2E |
| M129 | Admin 项目范围关系整组修订 | PRODUCT-01/02 + ARCH-10/19 + Revise Scope API + Admin Web build | 组织目录联动、OIDC、E2E |
| M130 | Admin 任务候选 MANUAL 分配 | PRODUCT-01/02 + ARCH-10/19 + Assign Candidates API + Admin Web build | 策略解析 UI、OIDC、E2E |
| M131 | Admin 运营异常详情与确认 | PRODUCT-01/02 + ARCH-10/19 + getOperationalException API + Admin Web build | 异常自动解决、OIDC、E2E |
| M132 | Admin 工单 SLA 实例列表与深链 | PRODUCT-01/02 + ARCH-10/19 + listWorkOrderSlaInstances API + Admin Web build | SLA 策略编辑、OIDC、E2E |
| M133 | Admin 表单/资料详情读取与 StoredFile 作废 | PRODUCT-01/02 + ARCH-10/11/19 + Detail/Invalidate APIs + Admin Web build | 在线预览器、OIDC、E2E |
| M134 | Admin 工单权威详情/Stage/Task/核心时间线；Admin 试点可运行基线增加 CI build、开发态 Keycloak PKCE 与真实 Backend/PostgreSQL/Chrome E2E，并以 PR 阻断门禁覆盖 Task MANUAL assign-candidates/claim/release 局部写链路 | PRODUCT-01/02 + ARCH-10/19 + WorkOrder authority APIs + Task Assignment/Human Task Commands + Admin Web build/E2E + `admin-pilot-readiness-acceptance.md` | 结算时间线、正式企业 OIDC/BFF、接单至完结的完整履约写链路 E2E |
