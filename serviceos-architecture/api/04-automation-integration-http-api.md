---
title: 派单、SLA、集成、通知与异常 HTTP API
version: 0.1.0
status: Proposed
---

# 派单、SLA、集成、通知与异常 HTTP API

> M56～M57 已实现 BYD 入站与授权摘要。M58 在 Core OpenAPI 0.31.0 实现
> `POST /api/v1/internal/integration/byd/review-submissions` 与
> `GET /api/v1/outbound-deliveries/{id}`，并在 BYD OpenAPI 0.3.0 固定外发提审协议。
> M59 在 Core OpenAPI 0.32.0 实现单笔 UNKNOWN Delivery 人工重发。
> M60 未新增 HTTP API；严格 ACK 后由 recovered@v1 驱动 Operations 自动解决对应异常，保持
> “API 不直接标记业务成功”的边界。
> M62 在 Core OpenAPI 0.33.0 实现 SLA 项目工作台、工单时间线和实例详情三个只读查询；全部要求
> `sla.read` + 实时 Project Scope，动态时间以服务端 `asOf` 为准。
> M63 在 Core OpenAPI 0.34.0 将工作台 `projectId` 放宽为可选；省略时实时合并 TENANT/PROJECT
> RoleGrant，并以单条范围化 SQL 查询。M64～M65 通过 Project 有效期关系补充精确 REGION/NETWORK
> 项目映射；M66 已支持显式即时整组修订这些关系。ServiceNetwork 目录、生命周期与本章服务网络治理接口仍未实现。
> 通用 Connector/CreateOutboundDelivery、其他人工处置、批量 Replay 与其余本章接口仍为 Proposed。

沿用既有 API 的认证、幂等、`If-Match`、Problem Details、correlation 和审计约定。自动任务内部接口使用专用服务主体，不向普通用户令牌开放。

## 1. 服务网络

| 方法与路径 | 用途/命令 | 关键载荷 | 成功 |
|---|---|---|---|
| `GET /service-networks` | 按项目、区域、状态、能力查询 | query | 200 |
| `GET /service-networks/{id}` | 网点、覆盖、能力、资质和指标摘要 | — | 200 |
| `POST /service-networks/{id}:suspend-dispatch` | SuspendNetworkDispatch | scope、reason、effectiveWindow、approvalRef? | 200 |
| `POST /service-networks/{id}:resume-dispatch` | ResumeNetworkDispatch | scope、reason | 200 |
| `POST /service-networks/{id}/qualifications` | RegisterQualification | type、validity、evidenceRef | 201 |
| `GET /service-networks/{id}/technician-memberships` | 本网点师傅关系 | status、capability、qualification | 200 |
| `POST /service-networks/{id}/technician-memberships` | RequestNetworkTechnicianMembership | technician/principalRef、validity、reason | 201/202 |
| `POST /network-technician-memberships/{id}:activate` | ActivateMembership | approvalRef? | 200 |
| `POST /network-technician-memberships/{id}:suspend` | SuspendMembership | reason、effectiveAt | 200/202 |
| `POST /network-technician-memberships/{id}:end` | EndMembership | reason、effectiveAt、reassignmentPlanRef? | 200/202 |
| `GET /technicians/{id}` | 师傅状态、网点关系、能力和资质摘要 | — | 200 |
| `POST /technicians/{id}/qualifications` | RegisterTechnicianQualification | type、validity、evidenceRef | 201 |
| `POST /technicians/{id}/capability-versions` | PublishTechnicianCapability | items、effectiveAt、approvalRef? | 201 |
| `POST /dispatch-policy-adjustments` | RequestDispatchPolicyAdjustment | network、scope、changes、window、reason | 202 |
| `POST /dispatch-policy-adjustments/{id}:approve` | ApproveAdjustment | decision、note | 200 |

停派和恢复使用适用范围与生效区间，不直接更新简单布尔字段。

师傅 membership 不创建登录密码，只绑定身份系统 Principal/Person 引用。Activate 前校验身份绑定、网点状态和必要资料。RegisterQualification 只登记待验证材料；除非已发布政策允许可信来源自动确认，否则不能直接把资质标为 VERIFIED。Suspend/End 必须先完成未结 Task/Appointment/ServiceAssignment 影响分析，必要时返回异步 operation。

## 2. 派单

| 方法与路径 | 用途/命令 | 关键载荷 | 成功 |
|---|---|---|---|
| `POST /tasks/{taskId}/dispatch-requests` | RequestDispatch | targetType、policyVersionId? | 202 |
| `GET /dispatch-requests/{id}` | 请求状态和 decisions | — | 200 |
| `GET /dispatch-decisions/{id}` | 候选过滤、评分与解释 | — | 200 |
| `POST /dispatch-requests/{id}:select-candidate` | SelectDispatchCandidate | candidateId、reason、approvalRef? | 200 |
| `POST /service-assignments/{id}:reassign` | Reassign | targetCandidateId、reason、approvalRef? | 202 |
| `GET /work-orders/{id}/service-assignments` | 网点/师傅责任历史 | — | 200 |
| `POST /internal/service-assignment-activations/{sagaId}:abort` | AbortActivation（仅编排/授权修复） | reason、approvalRef?、expectedSagaStage | 202 |

### 2.1 人工选择

```json
{
  "candidateId": "NETWORK-100",
  "reason": "用户与项目经理协商指定",
  "acknowledgedRuleExceptions": [],
  "approvalRef": null
}
```

服务端重新校验硬过滤与容量。客户端不能提交分数或声称候选合格。

激活中断由 saga 自动重试。内部 abort 端点要求服务主体；当已进入 `SERVICE_SWITCHED` 时必须提供业务审批，并按安全补偿流程执行，不能直接清除 Task guard。

### 2.2 决策解释响应

```json
{
  "decisionId": "DSP-DEC-001",
  "policyVersionId": "DSP-POLICY-V4",
  "inputSnapshotDigest": "sha256:...",
  "selectionMode": "AUTO",
  "selectedCandidateId": "NETWORK-100",
  "candidates": [
    {
      "candidateId": "NETWORK-100",
      "eligible": true,
      "filterResults": [
        {"ruleCode": "NETWORK.ACTIVE", "passed": true}
      ],
      "scoreComponents": [
        {"metricCode": "capacity.remaining", "snapshotId": "METRIC-1", "normalized": 0.8, "weight": 0.3}
      ],
      "finalScore": 0.76,
      "rank": 1
    }
  ]
}
```

根据数据权限隐藏不应向普通用户展示的商业指标，但审计记录保留完整解释。

## 3. SLA

> M62 已发布前三个 GET 查询；M63 支持工作台显式 projectId 或省略后解析授权项目集合，工单查询
> 仍从 WorkOrder 权威事实解析 Project Scope；暂停、恢复、重算和 escalation 查询仍为 Proposed。

| 方法与路径 | 用途/命令 | 关键载荷 | 成功 |
|---|---|---|---|
| `GET /work-orders/{id}/sla-instances` | 工单/任务 SLA | — | 200 |
| `GET /sla-instances/{id}` | 策略、deadline、segments、milestones | — | 200 |
| `POST /sla-instances/{id}:pause` | PauseSla | reasonCode、note?、evidenceRefs?、approvalRef? | 200 |
| `POST /sla-instances/{id}:resume` | ResumeSla | reason、effectiveAt? | 200 |
| `POST /sla-instances/{id}:recalculate` | RequestSlaRecalculation | corrections、reason、approvalRef | 202 |
| `GET /sla-instances?status=BREACHED` | 超时列表 | query | 200 |
| `GET /sla-escalations/{id}` | 里程碑、收件人、通知及关联异常 | — | 200 |

客户端不能直接提交新 deadline 或把 BREACHED 改成 MET。服务端按锁定策略、日历和 segment 重算。

## 4. 连接器与入站记录

| 方法与路径 | 用途 | 关键载荷 | 成功 |
|---|---|---|---|
| `GET /connectors` | 连接器健康与版本摘要 | query | 200 |
| `GET /inbound-envelopes/{id}` | 原始消息摘要、映射和处理结果 | — | 200 |
| `POST /connectors/{code}/inbound` | API/Webhook 入站 | 外部原文 | 200/202 |
| `POST /connectors/{code}/file-batches` | 登记 Excel/SFTP 批次 | fileRef、digest、manifest | 202 |
| `GET /canonical-messages/{id}` | 标准消息和领域命令结果 | — | 200 |

入站端点的认证、签名和响应格式由 ConnectorDefinitionVersion 决定；通用管理 API 不替代车企专属协议适配器。

## 5. OutboundDelivery

| 方法与路径 | 用途/命令 | 关键载荷 | 成功 |
|---|---|---|---|
| `POST /outbound-deliveries` | CreateOutboundDelivery（内部编排） | connector、messageType、sourceRefs、mappingVersion | 201 |
| `GET /outbound-deliveries/{id}` | payload 摘要、attempts、acknowledgements | — | 200 |
| `POST /internal/integration/byd/review-submissions` | M58 已实现的 BYD 专用提审创建 | sourceReviewCaseId | 201 |
| `POST /outbound-deliveries/{id}:retry` | M59 单笔 UNKNOWN 人工重发 | expectedAggregateVersion、reason、approvalRef | 202 |
| `POST /outbound-deliveries/{id}:query-remote-status` | QueryRemoteStatus | reason | 202 |
| `POST /outbound-deliveries/{id}:record-manual-ack` | RecordManualAcknowledgement | result、externalRef、evidenceRefs、reason | 200 |
| `POST /replay-requests` | RequestReplay | deliveryIds、mode、reason | 202 |
| `POST /replay-requests/{id}:approve` | ApproveReplay | decision、limits | 200 |

人工重试不接受客户端修改原 payload。M59 仅实现单笔 BYD 提审 UNKNOWN 重发：USER principal
必须通过 HIGH capability、project scope、原因、审批引用和预期聚合版本门禁；服务端创建不可变
ReplayRequest 与新 Task，复用冻结 payload/external key，并保留旧 UNKNOWN Attempt。Task 仍唯一拥有
重试时钟，集成模块不另行安排 nextRetryAt。批量 ReplayRequest、人工确认/放弃仍为 Proposed。

## 6. 外部回执

```json
{
  "externalAckId": "ACK-8899",
  "deliveryBusinessKey": "WO-1:INSTALLATION_COMPLETE:V3",
  "ackType": "BUSINESS",
  "result": "REJECTED",
  "reasonCodes": ["CLIENT.EVIDENCE.REJECTED"],
  "affectedObjectRefs": [
    {"type": "EvidenceRevision", "id": "EVD-55", "version": 2}
  ],
  "payloadRef": "INBOUND-OBJECT-99"
}
```

连接器按外部回执 ID 和业务键幂等。冲突回执创建 `type=INTEGRATION` 的 OperationalException，不覆盖旧 acknowledgement。

## 7. 通知

| 方法与路径 | 用途/命令 | 关键载荷 | 成功 |
|---|---|---|---|
| `GET /notification-intents/{id}` | 收件人和渠道投递摘要 | — | 200 |
| `GET /notification-deliveries/{id}` | attempts 和 receipts | — | 200 |
| `POST /notification-deliveries/{id}:retry` | RetryNotification | reason | 202 |
| `POST /notification-deliveries/{id}:use-fallback-channel` | UseFallbackChannel | channel、reason | 202 |
| `GET /notification-preferences/me` | 用户偏好 | — | 200 |
| `PUT /notification-preferences/me` | 更新非必要渠道偏好 | preferences | 200 |

关键事务通知是否可关闭由合规策略决定，不能仅以用户偏好绕过。

`RetryNotification` 和备用渠道动作均委托关联自动 Task 执行；NotificationDelivery/Attempt 不拥有独立业务重试调度。

## 8. 运营异常

| 方法与路径 | 用途/命令 | 关键载荷 | 成功 |
|---|---|---|---|
| `GET /operational-exceptions` | 异常工作台筛选 | type、severity、project、status、slaRisk | 200 |
| `GET /operational-exceptions/{id}` | occurrences、处理 Task 和允许动作 | — | 200 |
| `POST /operational-exceptions/{id}:acknowledge` | AcknowledgeException | note? | 200 |
| `POST /operational-exceptions/{id}:resolve` | ResolveException | resolutionCode、domainActionRef、evidenceRefs | 200 |
| `POST /operational-exceptions/{id}:verify-close` | VerifyAndCloseException | verificationResult、evidenceRefs? | 200 |
| `POST /operational-exceptions/{id}:suppress` | SuppressDuplicate | reason、approvalRef、until? | 200 |

Resolve 必须引用已经执行成功的领域命令、重放或修复结果；API 不提供“直接标记业务成功”。

## 9. 事件目录

| 事件 | 关键载荷 |
|---|---|
| `DispatchCompleted` | request、decision、service assignment、policy version |
| `DispatchFailed` | request、failure class、attempt、manual task ref? |
| `SlaWarningReached` | instance、milestone、current responsibility refs |
| `SlaBreached` | instance、deadline、breach duration、policy version |
| `OutboundDeliveryFailed` | delivery、attempt、error class、retry plan |
| `ExternalBusinessRejected` | delivery、acknowledgement、affected refs |
| `NotificationFailedFinal` | delivery、channel、criticality、error class |
| `OperationalExceptionOpened` | exception、type、severity、source ref |
| `OperationalExceptionResolved` | exception、resolution/action refs |

事件不包含完整候选商业指标、原始报文、联系方式或通知正文。

## 10. 错误码补充

| 错误码 | HTTP | 含义 |
|---|---:|---|
| `NO_ELIGIBLE_DISPATCH_CANDIDATE` | 422 | 没有合格候选，已转人工 |
| `CAPACITY_RESERVATION_CONFLICT` | 409 | 容量被并发占用 |
| `DISPATCH_HARD_RULE_VIOLATION` | 422 | 人工候选违反不可覆盖规则 |
| `SLA_PAUSE_NOT_ALLOWED` | 422 | 原因/角色/状态不允许暂停 |
| `SLA_ALREADY_BREACHED` | 409 | 不允许用普通命令消除超时 |
| `DELIVERY_PAYLOAD_IMMUTABLE` | 409 | 不允许修改已创建 payload |
| `EXTERNAL_ACK_CONFLICT` | 409 | 外部回执相互冲突 |
| `REPLAY_APPROVAL_REQUIRED` | 403/422 | 批量或高风险重放需要审批 |
| `EXCEPTION_RESOLUTION_UNVERIFIED` | 422 | 缺少真实恢复动作或验证 |
