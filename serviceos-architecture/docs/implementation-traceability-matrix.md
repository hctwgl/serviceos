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
| project | ARCH-01/03/05 | API-07 | DATA-01 | M2 CFG/WO、M7 ADM | E0/E2 |
| authorization | ARCH-07、ARCH-21 | API-01/02 | DATA-02 | M2 AUTH、M6 SEC | E1 |
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
| sla | ARCH-12、M61～M63 | sla.started/breached/met@v1；API-04、OpenAPI Core 0.34.0 | DATA-04、V061～V063 | M4 SLA、M61～M63 | E4 |
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
