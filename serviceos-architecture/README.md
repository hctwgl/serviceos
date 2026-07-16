# ServiceOS Architecture Book

ServiceOS 是面向新能源充电设施现场服务的履约平台。本仓库是产品、业务、研发、测试和运维共同使用的架构事实源。

首个落地行业是新能源汽车家充勘测、安装、维修和拆装；平台边界面向可复用的现场履约能力，而不是为每家车企复制业务代码。

## 阅读顺序

1. [产品宪法](architecture/00-product-constitution.md)
2. [业务领域](architecture/01-business-domain.md)
3. [业务能力地图](architecture/02-capability-map.md)
4. [核心领域模型](architecture/03-domain-model.md)
5. [统一领域语言](domain/00-ubiquitous-language.md)
6. [限界上下文地图](domain/01-context-map.md)
7. [聚合目录与不变量](domain/02-aggregate-catalog.md)
8. [ADR-020 核心领域命名与边界稳定策略](decisions/ADR-020-canonical-domain-language.md)
9. [履约事实、计价与结算](architecture/04-fulfillment-pricing-settlement.md)
10. [配置资产与版本中心](architecture/05-configuration-version-center.md)
11. [工单、任务与流程执行内核](architecture/06-work-order-task-execution-kernel.md)
12. [核心命令与事件契约](api/01-command-event-contracts.md)
13. [工单与任务 HTTP API](api/02-work-order-task-http-api.md)
14. [配置与执行内核逻辑数据模型](data/01-execution-logical-model.md)
15. [身份、授权与审计](architecture/07-identity-authorization-audit.md)
16. [预约与现场作业](architecture/08-appointment-field-operations.md)
17. [动态表单与字段引擎](architecture/09-dynamic-form-field-engine.md)
18. [资料、审核与整改闭环](architecture/10-evidence-review-correction.md)
19. [现场作业 HTTP API](api/03-field-operations-http-api.md)
20. [现场作业逻辑数据模型](data/03-field-operations-logical-model.md)
21. [服务网络与派单引擎](architecture/11-service-network-dispatch.md)
22. [SLA 时钟、预警与升级](architecture/12-sla-clock-escalation.md)
23. [车企集成、回传与可靠交付](architecture/13-integration-reliability.md)
24. [通知与运营异常中心](architecture/14-notification-operational-exception.md)
25. [M4 自动化 HTTP API](api/04-automation-integration-http-api.md)
26. [M4 逻辑数据模型](data/04-automation-integration-logical-model.md)
27. [履约事实提取与双向试算运行时](architecture/15-fulfillment-fact-calculation-runtime.md)
28. [对账、结算、争议与调整边界](architecture/16-reconciliation-settlement-boundary.md)
29. [历史数据迁移、双轨与切换](architecture/17-data-migration-cutover.md)
30. [试点、灰度发布与可观测性](architecture/18-pilot-rollout-observability.md)
31. [M5 HTTP API](api/05-pricing-migration-pilot-http-api.md)
32. [M5 逻辑数据模型](data/05-pricing-migration-pilot-logical-model.md)
33. [授权与审计逻辑数据模型](data/02-authorization-audit-logical-model.md)
34. [M2 执行内核验收矩阵](testing/01-m2-execution-acceptance-matrix.md)
35. [M3 现场作业验收矩阵](testing/02-m3-field-operations-acceptance-matrix.md)
36. [M4 自动化验收矩阵](testing/03-m4-automation-integration-acceptance.md)
37. [M5 试算与试点验收矩阵](testing/04-m5-pricing-migration-pilot-acceptance.md)
38. [研发工程、模块与应用服务实施蓝图](architecture/19-engineering-module-blueprint.md)
39. [事务、消息、幂等与并发实施蓝图](architecture/20-transaction-messaging-concurrency-blueprint.md)
40. [安全、非功能、部署与运维实施蓝图](architecture/21-security-nfr-deployment-blueprint.md)
41. [M6 工程就绪验收矩阵](testing/05-m6-engineering-readiness-acceptance.md)
42. [M6 研发交付计划](roadmap/01-m6-engineering-delivery-plan.md)
43. [MVP 与实施路线](roadmap/00-mvp-roadmap.md)
44. [已确认业务事实](research/00-confirmed-business-facts.md)
45. [M1 业务资产基线填写手册](research/01-m1-business-asset-pack.md)
46. [术语表](docs/glossary.md)
47. [研发模块与架构证据追踪矩阵](docs/implementation-traceability-matrix.md)
48. [跨 Portal 信息架构](product/01-cross-portal-information-architecture.md)
49. [总部运营后台产品规格](product/02-admin-operations-portal-spec.md)
50. [网点协作 Portal 产品规格](product/03-network-portal-spec.md)
51. [师傅移动端产品规格](product/04-technician-mobile-app-spec.md)
52. [跨 Portal 协作与状态交互](product/05-cross-portal-interaction-state-spec.md)
53. [设计系统与可访问性](product/06-design-system-accessibility-spec.md)
54. [页面动作权限矩阵](product/07-page-action-permission-matrix.md)
55. [应用查询与偏好 API](api/06-application-query-preference-http-api.md)
56. [应用投影与偏好数据模型](data/06-application-projection-preference-logical-model.md)
57. [M7 应用体验验收矩阵](testing/06-m7-application-experience-acceptance.md)
58. [M7 应用交付计划](roadmap/02-m7-application-delivery-plan.md)
59. [项目、配置、授权与审计治理 API](api/07-project-configuration-access-governance-http-api.md)
60. [M8 工程参考实现与首条事务纵向切片](architecture/22-engineering-reference-implementation.md)
61. [M9 身份、授权与可靠消息执行参考实现](architecture/23-identity-authorization-reliable-worker-implementation.md)
62. [M9 身份授权与可靠消息验收矩阵](testing/07-m9-security-reliability-acceptance.md)
63. [M10 Task/Scheduler 与人工接管执行参考实现](architecture/24-task-scheduler-manual-intervention-implementation.md)
64. [M10 Task/Scheduler 与人工接管验收矩阵](testing/08-m10-task-scheduler-acceptance.md)
65. [M11 安全文件生命周期参考实现](architecture/25-secure-file-lifecycle-implementation.md)
66. [安全文件生命周期 HTTP API](api/08-secure-file-http-api.md)
67. [M11 安全文件生命周期验收矩阵](testing/09-m11-secure-file-acceptance.md)
68. [M12 契约兼容 CI 与客户端生成参考实现](architecture/26-contract-ci-client-generation-implementation.md)
69. [M12 契约兼容 CI 与客户端生成验收矩阵](testing/10-m12-contract-ci-acceptance.md)
70. [M13 可观测性、健康探针与日志脱敏参考实现](architecture/27-observability-health-redaction-implementation.md)
71. [M13 可观测性、健康探针与日志脱敏验收矩阵](testing/11-m13-observability-acceptance.md)
72. [ADR-016 单一镜像、独立迁移与失败关闭发布](decisions/ADR-016-single-image-explicit-migration-fail-closed-deployment.md)
73. [M14 容器、独立迁移与 staging 发布参考实现](architecture/28-container-staging-deployment-implementation.md)
74. [M14 容器、迁移、staging 与回滚验收矩阵](testing/12-m14-container-staging-deployment-acceptance.md)
75. [M16 配置发布解析与 BYD 工单接入事务切片](architecture/29-configuration-byd-work-order-intake-implementation.md)
76. [M16 配置发布解析与 BYD 工单接入验收矩阵](testing/13-m16-configuration-byd-work-order-intake-acceptance.md)
77. [M17 工作流可靠启动事务切片](architecture/30-workflow-bootstrap-runtime-implementation.md)
78. [M17 工作流可靠启动验收矩阵](testing/14-m17-workflow-bootstrap-acceptance.md)
79. [M18 TaskCompleted 与工作流线性推进事务切片](architecture/31-workflow-linear-progression-implementation.md)
80. [M18 TaskCompleted 与工作流线性推进验收矩阵](testing/15-m18-workflow-linear-progression-acceptance.md)
81. [M19 跨阶段、END 与工单履约完成事务切片](architecture/32-workflow-stage-completion-implementation.md)
82. [M19 跨阶段、END 与工单履约完成验收矩阵](testing/16-m19-workflow-stage-completion-acceptance.md)
83. [M20 人工工作流 Task 命令运行时](architecture/33-human-task-command-runtime-implementation.md)
84. [M20 人工工作流 Task 命令验收矩阵](testing/17-m20-human-task-command-acceptance.md)
85. [M21 TaskAssignment 候选与责任运行时](architecture/34-task-assignment-runtime-implementation.md)
86. [M21 TaskAssignment 候选与责任验收矩阵](testing/18-m21-task-assignment-acceptance.md)
87. [M22 TaskExecutionGuard 可靠运行时](architecture/35-task-execution-guard-runtime-implementation.md)
88. [M22 TaskExecutionGuard 验收矩阵](testing/19-m22-task-execution-guard-acceptance.md)
89. [M23 PREPARED TaskAssignment 可靠激活握手](architecture/36-prepared-task-assignment-handshake-implementation.md)
90. [M23 PREPARED TaskAssignment 验收矩阵](testing/20-m23-prepared-task-assignment-acceptance.md)
91. [M24 ServiceAssignment 与容量权威运行时](architecture/37-service-assignment-capacity-runtime-implementation.md)
92. [M24 ServiceAssignment 与容量权威验收矩阵](testing/21-m24-service-assignment-capacity-acceptance.md)
93. [M25 Dispatch 与 Task 改派 Inbox Saga](architecture/38-dispatch-task-reassignment-inbox-saga.md)
94. [M25 Dispatch 与 Task 改派 Saga 验收矩阵](testing/22-m25-dispatch-task-reassignment-saga-acceptance.md)
95. [M26 切换前可靠终止与持久检查点](architecture/39-pre-switch-abort-saga-implementation.md)
96. [M26 切换前可靠终止验收矩阵](testing/23-m26-pre-switch-abort-saga-acceptance.md)
97. [M27 ServiceAssignment 激活超时对账与人工接管](architecture/40-assignment-saga-timeout-reconciliation.md)
98. [M27 ServiceAssignment 激活超时对账验收矩阵](testing/24-m27-assignment-saga-timeout-acceptance.md)
99. [M28 ServiceAssignment 超时异常自动恢复](architecture/41-assignment-timeout-auto-recovery.md)
100. [M28 ServiceAssignment 超时异常自动恢复验收矩阵](testing/25-m28-assignment-timeout-auto-recovery-acceptance.md)
101. [M29 运营异常工作台](architecture/42-operational-exception-workbench-mybatis.md)
102. [M29 运营异常工作台验收矩阵](testing/26-m29-operational-exception-workbench-acceptance.md)
103. [M30 预约修订运行时](architecture/43-appointment-revision-concurrency-runtime.md)
104. [M30 预约修订验收矩阵](testing/27-m30-appointment-revision-acceptance.md)
105. [M31 预约联系与终态运行时](architecture/44-appointment-contact-terminal-runtime.md)
106. [M31 预约联系与终态验收矩阵](testing/28-m31-appointment-contact-terminal-acceptance.md)
107. [M32 Visit 现场到离场运行时](architecture/45-visit-lifecycle-runtime.md)
108. [M32 Visit 现场生命周期验收矩阵](testing/29-m32-visit-lifecycle-acceptance.md)
109. [M33 表单资产发布基础](architecture/46-form-asset-publication-foundation.md)
110. [M33 表单资产发布基础验收](testing/30-m33-form-asset-foundation-acceptance.md)
111. [M34 不可变表单提交运行时](architecture/47-form-submission-runtime.md)
112. [M34 不可变表单提交运行时验收](testing/31-m34-form-submission-runtime-acceptance.md)
113. [M35 表单任务完成引用门禁](architecture/48-form-task-completion-gate.md)
114. [M35 表单任务完成引用门禁验收](testing/32-m35-form-task-completion-gate-acceptance.md)
115. [M36 Evidence 资产发布基础](architecture/49-evidence-asset-publication-foundation.md)
116. [M36 Evidence 资产发布基础验收](testing/33-m36-evidence-asset-foundation-acceptance.md)
117. [M37 固定 EvidenceSlot 运行时](architecture/50-fixed-evidence-slot-runtime.md)
118. [M37 固定 EvidenceSlot 运行时验收](testing/34-m37-fixed-evidence-slot-runtime-acceptance.md)
119. [M38 EvidenceItem 与不可变 EvidenceRevision 运行时](architecture/51-evidence-item-revision-runtime.md)
120. [M38 EvidenceItem 与不可变 EvidenceRevision 验收](testing/35-m38-evidence-item-revision-acceptance.md)
121. [M39 Evidence 机器校验运行时](architecture/52-evidence-machine-validation-runtime.md)
122. [M39 Evidence 机器校验验收](testing/36-m39-evidence-machine-validation-acceptance.md)
123. [M40 EvidenceSetSnapshot 运行时](architecture/53-evidence-set-snapshot-runtime.md)
124. [M40 EvidenceSetSnapshot 验收](testing/37-m40-evidence-set-snapshot-acceptance.md)
125. [M41 EvidenceSetSnapshot Task 完成门禁](architecture/54-evidence-task-completion-gate.md)
126. [M41 EvidenceSetSnapshot Task 完成门禁验收](testing/38-m41-evidence-task-completion-gate-acceptance.md)
127. [M42 EvidenceRevision 作废运行时](architecture/55-evidence-invalidate-runtime.md)
128. [M42 EvidenceRevision 作废验收](testing/39-m42-evidence-invalidate-acceptance.md)
129. [M43 表单+资料双引用 Task 完成门禁](architecture/56-dual-input-task-completion-gate.md)
130. [M43 表单+资料双引用完成门禁验收](testing/40-m43-dual-input-task-completion-acceptance.md)
131. [M44 ReviewCase / ReviewDecision 最小运行时](architecture/57-review-case-decision-runtime.md)
132. [M44 ReviewCase / ReviewDecision 验收](testing/41-m44-review-case-decision-acceptance.md)
133. [M45 CorrectionCase 最小运行时](architecture/58-correction-case-runtime.md)
134. [M45 CorrectionCase 验收](testing/42-m45-correction-case-acceptance.md)
135. [M46 files StoredFile 作废联动](architecture/59-files-invalidation-linkage.md)
136. [M46 files 作废联动验收](testing/43-m46-files-invalidation-linkage-acceptance.md)
137. [M47 CorrectionCase 整改 Task 自动创建](architecture/60-correction-task-runtime.md)
138. [M47 整改 Task 验收](testing/44-m47-correction-task-acceptance.md)
139. [M48 ReviewCase 强制通过与重开](architecture/61-review-force-approve-reopen-runtime.md)
140. [M48 强制通过与重开验收](testing/45-m48-review-force-approve-reopen-acceptance.md)
141. [M49 ExternalReviewReceipt 最小运行时](architecture/62-external-review-receipt-runtime.md)
142. [M49 外部审核回执验收](testing/46-m49-external-review-receipt-acceptance.md)
143. [M50 整改 Task 自动候选人](architecture/63-correction-task-auto-candidate-runtime.md)
144. [M50 自动候选人验收](testing/47-m50-correction-auto-candidate-acceptance.md)
145. [M51 CorrectionCase WAIVED](architecture/64-correction-case-waive-runtime.md)
146. [M51 WAIVED 验收](testing/48-m51-correction-waive-acceptance.md)
147. [M52 条件 EvidenceSlot requiredWhen 运行时](architecture/65-conditional-evidence-slot-runtime.md)
148. [M52 条件 EvidenceSlot 验收](testing/49-m52-conditional-evidence-slot-acceptance.md)
149. [ADR-022 条件事实版本与资料槽位重解析（Accepted）](decisions/ADR-022-conditional-fact-version-and-evidence-reresolution.md)
150. [M53 表单条件与 EvidenceSlot 重解析运行时](architecture/66-m53-form-condition-evidence-reresolution-proposal.md)
151. [M53 表单条件与 EvidenceSlot 重解析验收矩阵](testing/50-m53-form-condition-evidence-reresolution-acceptance-proposal.md)
152. [M54 车企回执影响对象权威校验](architecture/67-m54-external-review-affected-target-validation.md)
153. [M54 车企回执影响对象权威校验验收矩阵](testing/51-m54-external-review-affected-target-validation-acceptance.md)
154. [M55 CLIENT ReviewCase 来源与回执批次门禁](architecture/68-m55-client-review-case-origin-runtime.md)
155. [M55 CLIENT ReviewCase 来源与回执批次门禁验收矩阵](testing/52-m55-client-review-case-origin-acceptance.md)
156. [M56 BYD CPIM InboundEnvelope 与 CanonicalMessage 权威入站事实](architecture/69-m56-inbound-envelope-canonical-message-runtime.md)
157. [M56 BYD CPIM InboundEnvelope 与 CanonicalMessage 验收矩阵](testing/53-m56-inbound-envelope-canonical-message-acceptance.md)
158. [M57 BYD 厂端审核回调权威入站运行时](architecture/70-m57-byd-review-callback-runtime.md)
159. [M57 BYD 厂端审核回调验收矩阵](testing/54-m57-byd-review-callback-acceptance.md)
160. [M58 BYD 提审 OutboundDelivery 可靠运行时](architecture/71-m58-byd-review-submission-outbound-delivery.md)
161. [M58 BYD 提审 OutboundDelivery 验收矩阵](testing/55-m58-byd-review-submission-outbound-delivery-acceptance.md)
162. [M59 UNKNOWN 外部交付人工重发运行时](architecture/72-m59-unknown-delivery-manual-replay.md)
163. [M59 UNKNOWN 外部交付人工重发验收矩阵](testing/56-m59-unknown-delivery-manual-replay-acceptance.md)
164. [M60 外部交付恢复异常自动闭环](architecture/73-m60-outbound-delivery-exception-auto-closure.md)
165. [M60 外部交付恢复异常自动闭环验收矩阵](testing/57-m60-outbound-delivery-exception-auto-closure-acceptance.md)
166. [M61 Task 自然时长 SLA 时钟](architecture/74-m61-task-elapsed-sla-clock.md)
167. [M61 Task 自然时长 SLA 时钟验收矩阵](testing/58-m61-task-elapsed-sla-clock-acceptance.md)
168. [M62 SLA 授权查询与工作台投影](architecture/75-m62-sla-authorized-query-projection.md)
169. [M62 SLA 授权查询与工作台投影验收矩阵](testing/59-m62-sla-authorized-query-acceptance.md)
170. [M63 授权项目集合与跨项目 SLA 队列](architecture/76-m63-authorized-project-scope-sla-queue.md)
171. [M63 授权项目集合与跨项目 SLA 队列验收矩阵](testing/60-m63-authorized-project-scope-sla-queue-acceptance.md)
172. [M64 项目区域关系与 REGION SLA 队列](architecture/77-m64-project-region-scope-sla-queue.md)
173. [M64 项目区域关系与 REGION SLA 队列验收矩阵](testing/61-m64-project-region-scope-sla-queue-acceptance.md)
174. [M65 项目网点关系与 NETWORK SLA 队列](architecture/78-m65-project-network-scope-sla-queue.md)
175. [M65 项目网点关系与 NETWORK SLA 队列验收矩阵](testing/62-m65-project-network-scope-sla-queue-acceptance.md)
176. [M66 项目范围关系整组修订](architecture/79-m66-project-scope-relation-revision.md)
177. [M66 项目范围关系整组修订验收矩阵](testing/63-m66-project-scope-relation-revision-acceptance.md)
178. [M67 项目授权目录与范围历史查询](architecture/80-m67-authorized-project-directory-query.md)
179. [M67 项目授权目录与范围历史查询验收矩阵](testing/64-m67-authorized-project-directory-query-acceptance.md)
180. [M68 授权工单目录与详情查询](architecture/81-m68-authorized-work-order-query.md)
181. [M68 授权工单目录与详情查询验收矩阵](testing/65-m68-authorized-work-order-query-acceptance.md)
182. [M69 授权工单执行工作区投影](architecture/82-m69-authorized-work-order-execution-projection.md)
183. [M69 授权工单执行工作区投影验收矩阵](testing/66-m69-authorized-work-order-execution-projection-acceptance.md)
184. [M70 授权任务队列与详情](architecture/83-m70-authorized-task-directory-detail.md)
185. [M70 授权任务队列与详情验收矩阵](testing/67-m70-authorized-task-directory-detail-acceptance.md)
186. [M71 Task 服务端允许动作投影](architecture/84-m71-task-server-driven-allowed-actions.md)
187. [M71 Task 服务端允许动作投影验收矩阵](testing/68-m71-task-server-driven-allowed-actions-acceptance.md)
188. [M72 Task 执行 Attempt 历史查询](architecture/85-m72-task-execution-attempt-history.md)
189. [M72 Task 执行 Attempt 历史查询验收矩阵](testing/69-m72-task-execution-attempt-history-acceptance.md)
190. [M73 工单核心执行时间线投影](architecture/86-m73-work-order-core-execution-timeline.md)
191. [M73 工单核心执行时间线投影验收矩阵](testing/70-m73-work-order-core-execution-timeline-acceptance.md)
192. [M74 工单现场履约时间线事件合并](architecture/87-m74-work-order-field-ops-timeline.md)
193. [M74 工单现场履约时间线事件合并验收矩阵](testing/71-m74-work-order-field-ops-timeline-acceptance.md)

## 仓库结构

```text
serviceos-architecture/
├── architecture/    # 产品与领域架构
├── domain/          # 统一领域语言、上下文地图与聚合目录
├── decisions/       # 架构决策记录（ADR）
├── docs/            # 术语、规范和索引
├── product/         # PRD、应用与交互设计
├── research/        # 已确认事实、待验证假设和调研材料
├── roadmap/         # MVP、里程碑与交付计划
├── api/             # API 与事件契约
├── data/            # 逻辑数据模型与数据字典
├── diagrams/        # 独立 Mermaid/PlantUML 源文件
└── testing/         # 架构验收矩阵与故障恢复场景
```

## 文档状态

文档头部使用以下状态：

- `Draft`：正在形成，不可作为研发承诺；
- `Proposed`：可评审的完整提案；
- `Accepted`：已通过决策，可指导实现；
- `Superseded`：已被新文档或 ADR 替代。

## 变更纪律

- 业务事实与架构建议必须分开记录；
- 会影响长期边界的选择必须写 ADR；
- 流程、表单、资料、规则、SLA、派单策略和价格方案必须版本化；
- 新车企或新品牌默认通过配置接入，不新增车企专属核心表或核心分支；
- MVP 只实现已验证的共性能力，通用平台能力按明确扩展点预留，不提前铺满。

## 当前基线

当前工程基线推进至 **M74**：Evidence 已覆盖条件槽位初次解析、表单事实只追加重解析、槽位世代、
条件变化人工处置、不可变 Item/Revision、机器校验、Snapshot、Task 完成门禁、作废与
Review/Correction，并建立 INTERNAL→CLIENT 审核来源及回执批次/mapping 冻结门禁；BYD 创建工单入口
已具备权威 InboundEnvelope、CanonicalMessage、私有原文留存、业务键幂等和崩溃恢复事实；BYD 厂端
审核结果已按显式订单路由、逐项 Canonical 和部分成功语义可靠进入 CLIENT ReviewCase；
BYD 提审已建立不可变 Delivery/Attempt/Acknowledgement，明确成功后自动创建 CLIENT Case/Route；
UNKNOWN 自动链路失败关闭，并可由获权 USER 携带原因、审批引用和预期版本创建不可变 ReplayRequest，
复用冻结 payload 由新 Task 人工重发，旧 UNKNOWN Attempt 不覆盖；重发取得严格 ACK 后会以恢复事件
幂等关闭该 Delivery 历次 UNKNOWN Task 的运营异常，且恢复/失败事件乱序时不产生无效 HUMAN Task；
Task 已可按 Workflow 显式 `slaRef` 锁定 ELAPSED SLA 策略，以创建/完成事件驱动开始、到期、MET 与
MET_LATE，并由 PostgreSQL 对账避免重复或完成事件延迟造成误报；SLA 实例现可经 `sla.read` 与实时
Project Scope 授权进入工作台、工单时间线和详情查询；Project REGION/NETWORK 有效期关系可将对应
RoleGrant 映射为同一工作台的精确项目集合，并可通过显式整组命令即时修订且保留历史；当前主体还可通过
`project.read` 安全查询授权项目目录、详情和范围修订历史；`task.read` 还提供实时授权范围内的独立
Task 队列、现有责任事实筛选和冻结执行引用详情；动态时间统一使用服务端
`asOf`，并可按现有人工命令的 capability、状态、责任与 execution guard 获取服务端 allowed-actions；
自动 Task 已持久化的执行 Attempt 还可在同一实时授权边界下按序查询，且不暴露 worker、payload 或错误正文。
独立 `readmodel` 模块现通过 Inbox 可靠消费 WorkOrder/Workflow/Stage/Task 与
Appointment/Visit/ContactAttempt 公开事件，提供实时授权、稳定分页且不含 PII/原始 payload 的工单
执行与现场履约时间线；Evidence/Review/Delivery/SLA/异常完整合并和投影重建作业仍未实现。
完整平台仍未完成：计算字段/脚本、
OCR/CV、复杂工作流、BUSINESS 日历与 SLA 暂停/预警/升级/通知、结算、正式基础设施和各 Portal 工程
仍属于明确未实现范围；也不代表所有 Proposed 文档均已接受。

核心业务编码前，必须使用 `research/templates/` 中的模板完成首个试点项目基线，并用真实脱敏工单完成桌面演练。
