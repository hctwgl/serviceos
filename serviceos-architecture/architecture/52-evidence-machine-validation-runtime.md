---
title: M39 Evidence 机器校验运行时
version: 0.1.0
status: Implemented
---

# M39 Evidence 机器校验运行时

M39 在 M38 扫描投影之上补齐确定性机器校验：`VALIDATING → VALIDATED | VALIDATION_FAILED`，
并持久化 append-only `EvidenceValidation` 事实。

本里程碑实现既有领域设计，不新增 ADR。

## 1. 触发与编排

1. Inbox 消费 `file.scan-completed@v1`（CLEAN）将 Revision 置为 `VALIDATING`；
2. 同一事务内通过 `TaskSchedulingService` 调度自动任务 `evidence.machine-validation`；
3. Worker 认领后执行确定性检查，写入校验事实，推进生命周期，刷新槽位数量投影；
4. 恶意扫描仍只进入 `QUARANTINED`，不调度机器校验。

对象存储与扫描 I/O 仍在 files 模块；evidence 只依赖 `task::api` / `task::spi` 与既有公开 API。

## 2. 检查范围

始终执行：

| checkType | 来源 | 失败关闭 |
|---|---|---|
| FORMAT | Slot `mediaType` ↔ Revision MIME | BLOCK |
| SIZE | 冻结 `capture.maxSizeBytes`（若配置） | BLOCK |
| CAPTURE_POLICY | `allowGallery` / `requireRealtimeCapture` / `requireGps` | BLOCK |

若模板声明：

| checkType | 行为 |
|---|---|
| DUPLICATE / HISTORICAL_IMAGE | 同租户同项目、计入状态 Revision 的 contentDigest 冲突 |
| 其他 BLOCK（OCR/BLUR 等） | `FAILED` + `UNSUPPORTED_CHECK_TYPE`（失败关闭） |
| 其他 WARN | `SKIPPED` + `DEFERRED_CHECK`（不阻塞 VALIDATED） |

仅 `severity=BLOCK` 且 `result=FAILED` 会使 Revision 进入 `VALIDATION_FAILED`；
失败版本不计入槽位数量投影。

## 3. 幂等与不可变

- 自动任务 `businessKey/payloadRef` = `evidenceRevisionId`；
- 已是 `VALIDATED` / `VALIDATION_FAILED` / `QUARANTINED` / `INVALIDATED` 时直接成功；
- `evd_evidence_validation` 按 `(tenant, revision, check_type)` 唯一，冲突 `DO NOTHING`；
- 校验事实触发器禁止 UPDATE/DELETE；
- Revision 状态更新带 `expectedStatus=VALIDATING` 条件。

## 4. API、事件与数据

- OpenAPI **0.14.0**：`EvidenceRevision.validations[]` 返回机器校验投影；
- 事件：`evidence.validation-completed@v1`，并继续发出
  `evidence.revision-validation-state-changed@v1`；
- V040 创建 `evd_evidence_validation`。

## 5. 明确未实现

1. OCR / SN / VIN / 模糊 / 光照等图像或业务字段校验实现；
2. GPS 权威距离核验（仅要求客户端 `locationClaim` 存在）；
3. `evidence.invalidate`、Review/Correction（EvidenceSetSnapshot 见 M40）；
4. Task 完成 Evidence 完整性门禁与 ADR-018 条件槽位。
