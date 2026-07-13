---
title: M11 安全文件生命周期验收矩阵
version: 0.1.0
status: Proposed
---

# M11 安全文件生命周期验收矩阵

| ID | Priority | 场景 | 预期证据 | 自动化层次 |
|---|---|---|---|---|
| M11-FILE-001 | P0 | Begin 同幂等键/同请求 | 同一 UploadSession/File，单会话写入 | Unit + PostgreSQL IT |
| M11-FILE-002 | P0 | 服务端生成 object key | key 不含文件名、actor、业务 ID，路径不可逃逸 | Unit |
| M11-FILE-003 | P0 | 短期上传授权 | token 绑定操作、key、精确大小、MIME、有效期 | Unit |
| M11-FILE-004 | P0 | token 篡改/过期 | ACCESS_DENIED/FILE_UPLOAD_EXPIRED，无对象写入 | Unit |
| M11-FILE-005 | P0 | 重复使用 PUT token | FILE_UPLOAD_CONFLICT，不覆盖原对象 | Unit |
| M11-FILE-006 | P0 | 假 MIME/错误大小/checksum | Finalize=FILE_OBJECT_MISMATCH，无 File/Task | Unit + PostgreSQL IT |
| M11-FILE-007 | P0 | Finalize 重放 | 同一 fileId，单 StoredFile、单扫描 Task | PostgreSQL IT |
| M11-FILE-008 | P0 | Finalize 事务边界 | 大对象 I/O 在事务外；File + scan Task 同事务 | Code review + PostgreSQL IT |
| M11-FILE-009 | P0 | 会话过期 | 先提交 EXPIRED，再返回 FILE_UPLOAD_EXPIRED | PostgreSQL IT |
| M11-SCAN-001 | P0 | CLEAN 内容 | 追加 ScanResult，File=AVAILABLE，Outbox 同事务 | Unit + PostgreSQL IT |
| M11-SCAN-002 | P0 | EICAR/恶意内容 | File=QUARANTINED/MALWARE，无下载授权 | Unit + PostgreSQL IT |
| M11-SCAN-003 | P0 | 扫描暂时失败 | 显式 RETRYABLE，Task 拥有唯一 retryAt | Unit/M10 worker |
| M11-DL-001 | P0 | 扫描前下载 | FILE_NOT_AVAILABLE，不生成授权记录 | PostgreSQL IT |
| M11-DL-002 | P0 | AVAILABLE 文件下载 | 实时 RoleGrant + purpose 审计 + 短期 URL | PostgreSQL IT |
| M11-SEC-001 | P0 | 控制面身份 | 无 JWT=401；伪造 tenant/actor 头无效 | Web MVC |
| M11-SEC-002 | P0 | 数据面响应 | 私有 no-store + nosniff，不公开目录/object key | Unit + Web contract |
| M11-CON-001 | P0 | HTTP 契约 | OpenAPI 3.1 无解析错误，含三段式控制面 | Contract test |
| M11-CON-002 | P0 | 扫描事件 | 示例通过 JSON Schema 2020-12 | Contract test |
| M11-DB-001 | P0 | V010 迁移 | 10 个迁移已应用，重复 migrate=0 | PostgreSQL IT |

## 环境门禁

本地无 Docker/兼容容器运行时时，Testcontainers PostgreSQL IT 会明确跳过。CI 必须先执行 `docker info` 并在成功后运行：

```bash
./mvnw clean verify
```

只运行 Unit/Contract，或只看到 IT 编译成功，不能宣称 M11 数据库 P0 已通过。

## 非本矩阵完成项

生产对象存储、正式反病毒、multipart、临时对象清理、EvidenceSlot 关系、OCR/图片质量、保留销毁和容量/SLO 仍需后续里程碑与环境证据。
