---
title: M42 EvidenceRevision 作废运行时
version: 0.1.0
status: Implemented
---

# M42 EvidenceRevision 作废运行时

## 1. 实现边界

M42 实现授权作废命令，使 `VALIDATED` EvidenceRevision 进入 `INVALIDATED`：

- 仅允许 `VALIDATED → INVALIDATED`；其他状态失败关闭为 `EVIDENCE_REVISION_NOT_INVALIDATABLE`；
- 必须提供非空有界 `reasonCode`；可选 `approvalRef` 仅作审计/事件元数据；
- Capability：`evidence.invalidate`，项目范围取自 Revision 自身，不信任客户端 tenant/project；
- 同事务：CAS 更新、槽位数量投影刷新、审计、Outbox、幂等结果；
- `INVALIDATED` 不计入槽位数量（与 M38 投影规则一致）；
- 已写入的 EvidenceSetSnapshot / 成员不得被改写；作废后不可再被新 Snapshot 选入；
- 不调用 files 模块作废文件对象；不打开 Review/Correction；不改写已完成 Task 的 resultRef。

## 2. API 与事件

- `POST /api/v1/evidence-revisions/{revisionId}:invalidate`
- OpenAPI **0.17.0**
- 事件：`evidence.revision-invalidated@v1`
- Flyway **V042**；staging 期望 **042/44**

## 3. 未实现范围

1. files StoredFile 作废/下载语义联动；
2. ReviewCase / CorrectionCase / 多轮补传编排；
3. 已完成 Task 的自动重开或 Snapshot 替换；
4. ADR-018 条件槽位重解析；
5. 表单+资料双引用完成条件。
