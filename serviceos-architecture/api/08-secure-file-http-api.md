---
title: 安全文件生命周期 HTTP API
version: 0.1.0
status: Proposed
---

# 安全文件生命周期 HTTP API

机器可读权威契约位于 `serviceos-contracts/src/main/resources/openapi/serviceos-core-v1.yaml`。本文件解释业务语义，不复制全部 Schema。

## 1. 身份与权限

| 动作 | Capability | 身份来源 |
|---|---|---|
| Begin/Finalize | `file.upload` | OIDC JWT → CurrentPrincipal + 实时 RoleGrant |
| 下载授权 | `file.download` | OIDC JWT → CurrentPrincipal + 实时 RoleGrant |
| 短期 PUT/GET | 范围受限能力 token | object storage 签名，不接受 tenant/actor 参数 |

## 2. 控制面

| HTTP | 语义 | 成功 |
|---|---|---|
| `POST /api/v1/files/upload-sessions` | BeginUpload；服务端 key + 短期 PUT 授权 | 201 |
| `POST /api/v1/files/upload-sessions/{id}:finalize` | 校验真实对象并创建隔离文件/扫描任务 | 201 |
| `POST /api/v1/files/{id}/download-authorizations` | AVAILABLE 文件的实时短期下载授权 | 201 |

Begin 要求 `Idempotency-Key`。Finalize 使用正文中的 `finalizeCommandId` 同时作为设备/离线和传输幂等键，避免维护两套可能互相矛盾的键。

## 3. BeginUpload

输入包含业务上下文引用、仅展示的原文件名、声明 MIME、精确 size 和客户端 SHA-256。响应包含：

- `uploadSessionId` 与未来稳定 `fileId`；
- `uploadMethod/uploadUrl/requiredHeaders`；
- 上传授权与会话各自的过期时间。

幂等重放若会话已结束，响应仍返回同一会话和文件身份，但不会重新签发可覆盖对象的 PUT URL。

## 4. FinalizeUpload

Finalize 执行以下校验：

1. 当前主体对会话所在租户拥有 `file.upload`；
2. 会话有效且 Finalize 租约可获得；
3. 实际对象 key 与会话一致（客户端无法提交 key）；
4. 实际 size 与会话完全相等；
5. 服务端重新计算的 SHA-256 与预期完全相等；
6. 魔数识别 MIME 与声明兼容；
7. `StoredFile(QUARANTINED/PENDING_SCAN)` 与扫描 Task 原子提交。

成功不代表文件可供业务使用。调用方必须等待 `file.scan-completed` 或查询后续投影；只有 AVAILABLE 可被 EvidenceRevision 引用。

## 5. 下载授权

下载请求必须给出可审计 `purpose`。服务端实时执行授权并确认文件 AVAILABLE，随后保存 `fil_download_authorization` 并返回分钟级 URL。URL 不应持久化到业务表、日志或消息。

## 6. 稳定错误码

| errorCode | HTTP | 语义 |
|---|---:|---|
| `UNAUTHENTICATED` | 401 | 缺少/无效 OIDC token |
| `ACCESS_DENIED` | 403 | RoleGrant 或签名能力不允许 |
| `VALIDATION_FAILED` | 400 | 字段、文件名、MIME 或摘要格式非法 |
| `IDEMPOTENCY_KEY_REUSED` | 409 | Begin 幂等键被不同请求复用 |
| `FILE_UPLOAD_CONFLICT` | 409 | 会话/对象已消费或不可继续 |
| `FILE_FINALIZE_IN_PROGRESS` | 409 | 活跃 Finalize 租约存在 |
| `FILE_UPLOAD_EXPIRED` | 410 | 会话或短期凭证过期 |
| `FILE_OBJECT_MISMATCH` | 422 | size/checksum/魔数 MIME 不一致 |
| `RESOURCE_NOT_FOUND` | 404 | 当前租户不存在目标资源 |
| `FILE_NOT_AVAILABLE` | 423 | 文件尚未 CLEAN 或已隔离/失效 |

客户端只能依赖 `errorCode`，不能解析 detail 文案。
