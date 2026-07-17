---
title: ADR-039：Network Portal 资料代补（onBehalf）适配器
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
  - Evidence Owner
related_adrs:
  - decisions/ADR-001-modular-monolith-first.md
  - decisions/ADR-013-reference-engineering-profile-and-module-enforcement.md
  - decisions/ADR-026-portal-context-navigation-in-authorization.md
  - decisions/ADR-032-network-portal-read-apis.md
  - decisions/ADR-038-network-portal-reassign-technician.md
---

# ADR-039：Network Portal 资料代补（onBehalf）适配器

## 1. 状态与已接受决策

本 ADR 作为 M201 的边界与授权结论，正式接受：

1. Network Portal **写命令**扩展「资料代补 / onBehalf」；**不**新建 portal 模块、**不**新建并行资料状态机；
2. HTTP（Core OpenAPI `0.93.0`）：
   - `POST /api/v1/network-portal/tasks/{taskId}/evidence-slots/{slotId}/upload-sessions`
   - `POST /api/v1/network-portal/tasks/{taskId}/evidence-slots/{slotId}/upload-sessions/{uploadSessionId}:finalize`
   - `POST /api/v1/network-portal/correction-cases/{correctionCaseId}:resubmit`
3. **请求体（begin）**：沿用 Admin BeginEvidenceUpload 字段，并增加 **权威业务字段**
   `{ onBehalfOf, onBehalfReason, ...upload fields }`；
   客户端不得在 `captureMetadata` 内伪造 `onBehalfOf` / `delegationRef` / `onBehalfReason`
   （服务端剥离并仅接受命令级字段）；
4. **上下文**：`X-Network-Context` 必填（同 ADR-032）；服务端强制 ACTIVE NetworkMembership +
   任务 ACTIVE NETWORK 责任 = 上下文网点；
5. **前置失败关闭**：
   - 主体对上下文网点持有 ACTIVE `NetworkMembership`，否则 `PORTAL_CONTEXT_INVALID`；
   - 任务存在未关闭整改（CorrectionCase 状态 ∈ `OPEN` / `IN_PROGRESS` / `RESUBMITTED`），
     否则 `VALIDATION_FAILED`（本切片以整改代补为触发场景，不开放任意槽位代补）；
   - `onBehalfOf` 必须等于该任务 ACTIVE TECHNICIAN 责任人（assignee 字符串），
     且该师傅对本网点持有 ACTIVE `NetworkTechnicianMembership`；无 ACTIVE TECHNICIAN →
     `VALIDATION_FAILED`（应先指派/改派）；
   - 普通 Admin `evidence.submit` 路径继续失败关闭客户端 `onBehalfOf`（M38 语义保留）；
6. **能力**：种子 `evidence.submitOnBehalf`（HIGH）。Portal 门禁与领域代补路径均按
   **NETWORK scope** 校验该能力；底层 `file.upload` 仍按既有 TENANT 能力校验
   （与 Admin 上传一致，测试与运营授予一并覆盖）；
7. **领域启用**：`EvidenceCommandService.beginUploadOnBehalf` / `finalizeUploadOnBehalf`
   绕过「主体 = Task.responsiblePrincipalId」检查，但保留 RUNNING HUMAN、未 Guard、
   会话归属（`createdBy` = 实际上传人）约束；规范化 CaptureMetadata 写入
   `uploadedBy`（实际操作者）、`onBehalfOf`、`onBehalfReason`、`uploadedRole=NETWORK_OPERATOR`；
8. **编排归属**：`evidence` 模块提供 Portal 写适配器；依赖 `dispatch::api` /
   `network::api` 做责任与成员校验；复用既有 Begin/Finalize 与 CorrectionCase.resubmit；
9. Page Registry：`NETWORK.EVIDENCE.SUPPLEMENT`（catalog → `page-registry-v8`）；
10. **不**接受：跨网点代补、表单字段代改、Visit check-in、离线工作包、OCR/GPS 权威校验、
    槽位级 `allowOnBehalf` 配置表、完整 product/03 设计系统、Consumer Identity。

## 2. 上下文

M196～M200 已交付 Network Portal 指派/改派与预约协作写命令；M38 将 `onBehalfOf`
失败关闭。产品规格（`product/03` §9、`product/07`）要求网点在整改场景下可代补资料并
保留实际人与代办关系。本 ADR 窄接受该写切片，并以 OPEN 整改 + ACTIVE 师傅责任作为
「资料项允许代补」的可验证代理条件，避免在无配置模型时发明槽位策略表。

## 3. 后果

- OpenAPI 从 `0.92.0` 升至 `0.93.0`；Flyway V099 种子 `evidence.submitOnBehalf`（099/101）；
- `evidence` 模块 `package-info` 增加 `dispatch::api`、`network::api`；
- Admin Web Network Portal 增加代补上传与整改 resubmit 控件；
- 槽位策略表、离线包与表单代改若需要，须另接受切片。
