---
title: M38 EvidenceItem 与不可变 EvidenceRevision 运行时
version: 0.1.0
status: Implemented
---

# M38 EvidenceItem 与不可变 EvidenceRevision 运行时

M38 在 M37 固定 EvidenceSlot 之上打通安全文件生命周期的第一条纵向链路：Begin → Finalize →
EvidenceItem / 不可变 EvidenceRevision → 扫描状态关联 → 槽位数量投影 → 授权只读查询。

本里程碑实现既有领域设计，不新增 ADR。

## 1. 模块集成方式

选择 **evidence 编排 Begin/Finalize，files 仍权威维护文件事实**（方案 3）：

1. evidence 校验 Task、Slot、责任人、ExecutionGuard、`evidence.submit` 与 Project Scope；
2. 调用 `files::api` BeginUpload（`businessContextType=EvidenceSlot`）；
3. 客户端按 files 返回的短期凭证直传对象；
4. evidence Finalize 调用 `files::api` FinalizeUpload；
5. 文件事务提交成功后，evidence 在独立短事务中写入 Item/Revision/投影/审计/Outbox/幂等结果。

对象存储 I/O 不占用资料数据库事务。若文件已 Finalize 但资料写入失败，相同 `finalizeCommandId` /
`upload_session_id` 可安全重试补齐，由唯一约束防重。

evidence 只依赖 `files::api`，禁止读取 `fil_*` 内部表或复制文件状态机。

## 2. EvidenceItem / Revision

- EvidenceItem 属于唯一 Slot，序号在 Slot 下唯一；补传不覆盖 Item，而是新增 Revision；
- Finalize 成功前不存在 EvidenceRevision；对象存储回调不能创建 Revision；
- Revision 核心事实（fileObjectId、digest、MIME、大小、CaptureMetadata、upload session）不可变；
- 生命周期：`STORED → VALIDATING → VALIDATED|VALIDATION_FAILED|QUARANTINED`，以及
  `VALIDATED → INVALIDATED`；
- M38 通过 Inbox 消费 `file.scan-completed@v1`：CLEAN → `VALIDATING`，恶意隔离 → `QUARANTINED`；
- 机器校验与 `VALIDATED` 由后续 M39 完成；M38 本身不把扫描通过提升为 `VALIDATED`。

## 3. 数量投影规则

权威计数不按 Item 行数盲加，而是统计至少拥有一个计入状态 Revision 的 Item：

| Revision 状态 | 是否计入当前数量 |
|---|---|
| STORED | 是 |
| VALIDATING | 是 |
| VALIDATED | 是 |
| VALIDATION_FAILED | 否 |
| QUARANTINED | 否 |
| INVALIDATED | 否 |

投影写回 `evd_evidence_slot.status_projection`（`MISSING/PARTIAL/SATISFIED`）。maxCount 在
`SELECT ... FOR UPDATE` 槽位锁下强制执行，并发 Finalize 不得突破上限。

## 4. CaptureMetadata

M38 规范化并持久化最小可演进字段：`captureSource`、`capturedAt`、`receivedAt`、`deviceId`、
`appVersion`、`offlineFlag`、`uploadedBy`。客户端 location 仅作为 `locationClaim` 且
`locationVerified=false`。`onBehalfOf` / `delegationRef` 失败关闭。GPS/水印/EXIF 权威校验未完成。

## 5. API、授权与事件

- `POST /api/v1/tasks/{taskId}/evidence-slots/{slotId}/upload-sessions`
- `POST /api/v1/tasks/{taskId}/evidence-slots/{slotId}/upload-sessions/{uploadSessionId}:finalize`
- `GET /api/v1/tasks/{taskId}/evidence-items`
- `GET /api/v1/evidence-items/{itemId}`

Capability：`evidence.submit`（写入）、`evidence.read`（读取）；主体同时需要 `file.upload` 才能完成
files Begin/Finalize。`evidence.invalidate` 已登记但本切片不提供作废命令。

事件：

- `evidence.revision-created@v1`
- `evidence.revision-validation-state-changed@v1`

事件只含稳定 ID 与摘要，不含 URL、object key、原图元数据或完整敏感 CaptureMetadata。

## 6. 数据与校验

V039 创建 `evd_evidence_upload_session`、`evd_evidence_item`、`evd_evidence_revision`、
`evd_evidence_command_result`，并通过唯一约束与触发器保证：

- tenant/project/task/slot 归属一致；
- Item 序号与 Revision 版本唯一；
- upload session / finalizeCommandId / fileObjectId 幂等；
- Revision 核心事实与 Item 身份不可物理覆盖。

checksum 策略：同槽/跨槽相同摘要允许作为不同资料存在；幂等键是 upload session 与
finalizeCommandId，不是 checksum。重复资料告警留给后续校验。

## 7. 明确未实现

M38 不宣称 EVD-001～009 完成。仍未实现：

1. ADR-018 条件表达式与槽位重解析；
2. OCR/AI 图像与业务字段校验实现（M39 仅覆盖确定性规则）；
3. EvidenceSetSnapshot、ReviewCase、CorrectionCase；
4. 代办上传、`evidence.invalidate` 命令与授权下载编排；
5. Task 完成 Evidence 完整性门禁与正式报告。
