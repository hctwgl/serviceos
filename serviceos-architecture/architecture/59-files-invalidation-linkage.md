---
title: M46 files StoredFile 作废联动
version: 0.1.0
status: Implemented
---

# M46 files StoredFile 作废联动

## 1. 实现范围

1. `files` 新增 `AVAILABLE → INVALIDATED` 受控作废命令；
2. EvidenceRevision `invalidate` 在同事务内联动作废关联 `StoredFile`；
3. `INVALIDATED` 文件不可再获得下载授权（沿用既有 `AVAILABLE` 门禁）；
4. Capability：`file.invalidate`；Evidence 作废调用方需同时具备 `evidence.invalidate` 与 `file.invalidate`；
5. OpenAPI **0.21.0**；Flyway **V046**；staging **046/48**。

## 2. API / 事件

- `POST /api/v1/files/{fileId}:invalidate`
- `file.invalidated@v1`

## 3. 未实现

对象物理删除、Retention、正式对象存储、专业扫描服务替换。
