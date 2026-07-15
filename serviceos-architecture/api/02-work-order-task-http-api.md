---
title: 工单与任务 HTTP API 基线
version: 0.1.0
status: Proposed
---

# 工单与任务 HTTP API 基线

本文件定义 M2 可实现的 HTTP 契约基线。最终 OpenAPI 文件应从本基线生成或保持契约测试一致。

## 1. 通用约定

- 基础路径：`/api/v1`；
- JSON 使用 UTF-8，时间使用带时区 ISO 8601；
- 写请求必须携带 `Idempotency-Key`；
- 更新已有聚合必须携带 `If-Match: "<aggregateVersion>"`；
- 已分配权威系统的工单及其子域写命令必须携带 `X-Work-Order-Authority-Version`；
- 响应返回 `ETag`、`X-Correlation-Id`；
- 身份来自服务端认证上下文，不接受请求体自报角色；
- `202 Accepted` 仅用于真正异步且提供 operation 资源的命令；其余成功命令返回最终业务结果。

## 2. 错误模型

```json
{
  "type": "https://serviceos.example/problems/task-state-conflict",
  "title": "Task state conflict",
  "status": 409,
  "code": "TASK_STATE_CONFLICT",
  "detail": "Task must be RUNNING before completion.",
  "correlationId": "TRACE-...",
  "errors": [
    {"field": "resultRef", "code": "REQUIRED", "message": "结果引用不能为空"}
  ]
}
```

| HTTP | 错误码示例 | 含义 |
|---:|---|---|
| 400 | `VALIDATION_FAILED` | 格式或字段校验失败 |
| 401 | `UNAUTHENTICATED` | 未认证 |
| 403 | `ACTION_FORBIDDEN`、`DATA_SCOPE_DENIED` | 授权拒绝 |
| 404 | `WORK_ORDER_NOT_FOUND` | 对象不存在或为防泄露而隐藏 |
| 409 | `VERSION_CONFLICT`、`TASK_STATE_CONFLICT`、`IDEMPOTENCY_CONFLICT` | 并发/状态/幂等冲突 |
| 422 | `BUSINESS_PRECONDITION_FAILED`、`CONFIGURATION_NOT_RESOLVED` | 业务前置条件不满足 |
| 429 | `RATE_LIMITED` | 限流 |
| 503 | `DEPENDENCY_TEMPORARILY_UNAVAILABLE` | 可重试外部依赖失败 |

## 3. 工单命令 API

| 方法与路径 | 命令 | 必需载荷 | 成功 |
|---|---|---|---|
| `POST /work-orders` | CreateWorkOrder | source、externalRef、projectContext、serviceProduct、businessDate、initialData | 201 |
| `POST /work-orders/{id}:activate` | ActivateWorkOrder | reason? | 200 |
| `POST /work-orders/{id}:suspend` | SuspendWorkOrder | reasonCode、note、evidenceRefs? | 200 |
| `POST /work-orders/{id}:resume` | ResumeWorkOrder | reason、resumeAt? | 200 |
| `POST /work-orders/{id}:confirm-fulfillment` | ConfirmFulfillment | acceptanceRef、factSetVersion | 200 |
| `POST /work-orders/{id}:close` | CloseWorkOrder | reasonCode | 200 |
| `POST /work-orders/{id}:force-close` | ForceCloseWorkOrder | reasonCode、note、impactAcknowledgement、approvalRef | 200/202 |
| `POST /work-orders/{id}:cancel` | CancelWorkOrder | reasonCode、note | 200/202 |
| `POST /work-orders/{id}:reopen` | ReopenWorkOrder | mode、reason、recoveryPoint、approvalRef | 200/202 |
| `POST /work-orders/{id}:migrate-configuration` | MigrateConfiguration | migrationPlanId、approvalRef | 202 |
| `POST /work-orders/{id}/data-corrections` | CorrectWorkOrderData | fieldChanges、reason、evidenceRefs?、approvalRef? | 200/202 |

`mode` 枚举：`CORRECTION`、`RESTORE_CANCELLED`、`AUTHORIZED_CLOSED_REOPEN`。不同 mode 使用独立授权能力。

ForceCloseWorkOrder 不跳过补偿和影响分析：服务端计算未完成 Task、预约、ServiceAssignment、Delivery、资料/审核和试算影响；无法同步完成时返回 operation。它需要专用 capability、原因、影响确认和审批，不能复用普通 CloseWorkOrder 按钮。

CorrectWorkOrderData 仅更正由 workorder 拥有的基础字段，按 fieldCode 校验当前阶段、FieldPolicy、expectedValueDigest 和影响。表单提交、资料、审核、履约事实、预约和责任字段必须调用其拥有模块的更正命令；不能借此接口跨模块覆盖。更正只追加 WorkOrderDataCorrection，并触发派单/SLA/事实/投影影响分析。

### 3.1 创建工单载荷

```json
{
  "source": "BYD_API",
  "externalRef": {"orderNo": "EXT-001", "requestNo": "REQ-001"},
  "projectContext": {
    "clientCode": "BYD",
    "projectCode": "BYD-HOME-2026",
    "brandCode": "DYNASTY",
    "regionCode": "370200"
  },
  "serviceProductCode": "HOME_SURVEY_INSTALL",
  "businessDate": "2026-07-12",
  "initialData": {
    "customerRef": "CUSTOMER-001",
    "vehicleRef": "VEHICLE-001",
    "installationAddressRef": "ADDRESS-001"
  }
}
```

成功响应必须包含 `workOrderId`、`configurationBundleId`、生命周期和聚合版本。流程初始化通过 Outbox 异步启动，因此响应不承诺首批任务已经创建；返回 `initializationOperationId` 和任务查询链接。客户端可查询 operation 或工单任务列表，不能把“201 已创建工单”误解为“流程初始化已完成”。

进入 CreateWorkOrder 事务前，服务端依据 `tenant + source + external order + service product` 形成 creationBusinessKey，并通过 authority 模块幂等 ReserveCreationAuthority。只有 SERVICEOS authority 才创建权威工单；事务重新锁定并复核 assignment/version，成功响应同时返回 `authorityAssignmentId/authorityVersion`。LEGACY/SHADOW_ONLY 路由不在 ServiceOS 创建可写工单，返回/记录明确路由结果，不能双主写入。

## 4. 任务命令 API

| 方法与路径 | 命令 | 必需载荷 | 成功 |
|---|---|---|---|
| `POST /tasks/{id}:claim` | ClaimTask | 无 | 200 |
| `POST /tasks/{id}:release` | ReleaseTask | reason? | 200 |
| `POST /tasks/{id}:start` | StartTask | inputVersionRefs? | 200 |
| `POST /tasks/{id}:complete` | CompleteTask | actionCode、resultRef、inputVersionRefs、formSubmissionRef? | 200 |
| `POST /tasks/{id}:block` | BlockTask | reasonCode、note、evidenceRefs?、pauseSlaRequested | 200 |
| `POST /tasks/{id}:resolve-block` | ResolveBlock | resolution、evidenceRefs? | 200 |
| `POST /tasks/{id}:retry` | RetryTask | repairNote?、approvalRef? | 202 |
| `POST /tasks/{id}:cancel` | CancelTask | reasonCode、compensationPlanRef? | 200/202 |
| `POST /tasks/{id}:manual-complete` | ManualComplete | resultRef、repairDetails、reason、approvalRef | 200 |

自动任务的内部启动使用同一应用命令但不暴露给普通用户令牌；服务账号必须具备专用能力。

### 4.1 完成任务载荷

```json
{
  "actionCode": "INSTALLATION.SUBMIT",
  "resultRef": {
    "type": "InstallationSubmission",
    "id": "SUB-001",
    "version": 3
  },
  "inputVersionRefs": [
    {"type": "FormSubmission", "id": "FORM-SUB-1", "version": 4},
    {"type": "EvidenceSet", "id": "EVD-SET-1", "version": 8}
  ]
}
```

当前已实现的 OpenAPI 核心切片仍使用字符串 `resultRef + resultDigest`。对于 `formRef` 非空的
表单 Task，M35 规定 `resultRef=form-submission://{submissionId}`，`resultDigest` 必须等于该
`VALIDATED` submission 的 `contentDigest`；服务端同时复核 tenant、Task、Project、formKey 和
冻结 FormVersion。上述结构化 `inputVersionRefs` 是 EvidenceSet 等完成条件接入后的目标契约，
不得在客户端先行假设已经实现。

## 5. 查询 API

| 方法与路径 | 用途 |
|---|---|
| `GET /work-orders/{id}` | 工单概要及配置包、当前阶段摘要 |
| `GET /work-orders` | 按品牌、项目、区域、网点、师傅、阶段、SLA 风险分页查询 |
| `GET /work-orders/{id}/stages` | 阶段实例 |
| `GET /work-orders/{id}/tasks` | 工单任务与历史实例 |
| `GET /work-orders/{id}/timeline` | 用户时间线 |
| `GET /work-orders/{id}/data-corrections` | 基础字段更正版本和影响 |
| `GET /work-orders/{id}/allowed-actions` | 当前主体的工单动作及 obligations |
| `GET /tasks/{id}` | 任务详情、责任人、SLA 和结果引用 |
| `GET /tasks/{id}/allowed-actions` | 当前主体可执行动作及输入 Schema |
| `GET /tasks?assignee=me` | 个人待办 |
| `GET /operations/{operationId}` | 异步命令进度和最终结果 |

列表分页使用稳定排序和游标。数据范围在服务端应用；返回总数是否精确由查询场景决定并在响应中声明。

M73 已实现 `/work-orders/{id}/timeline` 的 WorkOrder/Workflow/Stage/Task 核心事件子集。Appointment、
Visit、Evidence/Review、Delivery、SLA、异常、试算与结算尚未合并，客户端不得把该子集标成完整跨域时间线。

## 6. 允许动作响应

```json
{
  "resourceVersion": 12,
  "actions": [
    {
      "code": "workOrder.reassignNetwork",
      "label": "改派网点",
      "inputSchemaRef": "schema://actions/reassign-network/v1",
      "obligations": ["REQUIRE_REASON", "SECOND_CONFIRMATION"]
    }
  ]
}
```

该响应用于界面渲染，不是后续命令的授权凭证。

## 7. 幂等行为

- 首次收到 key：创建 `PROCESSING` 记录并处理；
- 同 key、同摘要且已成功：返回原状态码、业务结果和资源链接；
- 同 key、同摘要且处理中：返回 409 `COMMAND_IN_PROGRESS` 或关联 operation；
- 同 key、不同摘要：返回 409 `IDEMPOTENCY_CONFLICT`；
- 服务端明确规定 key 保留期，外部工单创建幂等记录按业务保留期长期保存。

## 8. 异步命令

取消补偿、配置迁移和人工触发的外部重试可能返回 `202`：

```json
{
  "operationId": "OP-001",
  "status": "PENDING",
  "resource": "/operations/OP-001"
}
```

operation 状态：`PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED`。成功结果包含目标资源版本，失败包含稳定错误码和是否可重试。

## 9. 审计要求

所有写命令记录 commandId、主体、授权决策、原因、资源版本和结果。强制操作、配置迁移、人工完成自动任务及敏感数据查询按[身份、授权与审计设计](../architecture/07-identity-authorization-audit.md)记录增强审计。
