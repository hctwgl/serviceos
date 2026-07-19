---
title: M351 平台终审工作区只读组合查询
status: Implemented
milestone: M351
lastUpdated: 2026-07-19
relatedMilestones: [M90, M96, M44, M85]
openapiVersion: "1.0.48"
flywayVersion: "129"
---

# M351 平台终审工作区只读组合查询

## 目标

为管理端「工单详情 · 平台终审工作台」提供权威只读 Fan-in：
`GET /api/v1/work-orders/{workOrderId}/workspace/sections/FINAL_REVIEW`，
复用既有 ReviewCase / EvidenceSetSnapshot / Task / SLA，不新增第二套审核聚合。

说明：仓库曾有非正式分支名 `m351-m356-product-usability-golden-path`（产品可用性改造），
**未占用**正式里程碑文档编号；本切片起正式使用 M351。

## 范围与非目标

- 范围：
  - OpenAPI **1.0.48**：`getWorkOrderFinalReviewWorkspaceSection` + `{data, meta}` 富载荷
  - `POST /evidence-revisions/{revisionId}/download-authorizations`（purpose 如 `FINAL_REVIEW_PREVIEW`）
  - `WorkOrderQueryService.getMaskedContact` 服务端脱敏
  - `FinalReviewWorkspaceQueryService` 批量 Fan-in（Snapshot + Slots + Items/Revisions/Validations）
  - 顶层 workspace `sectionAvailability.FINAL_REVIEW`
  - SecurityTest + PostgresIT + 脱敏单元测试
- 明确不做：
  - 正式 `:decide` / targetDecisions（M353）
  - Admin 终审 UI（M352）
  - `review_target_decision` 表与 aggregate_version（M353）
  - 物理只读投影表
  - 预结算伪造

## 事实源

- `architecture/10-evidence-review-correction.md`
- `api/03-field-operations-http-api.md` §5 / 下载授权
- `api/06-application-query-preference-http-api.md` §5
- `data/03-field-operations-logical-model.md` review_* 实体

## 设计要点

1. **固定 path + 独立 operationId**：避免把终审富 DTO 塞进六选一 `WorkOrderWorkspaceSection`；
2. **查询不推进状态**；缺权次级能力局部降级，不在外层事务内吞掉嵌套 ACCESS_DENIED；
3. **批量装载**：一次 Snapshot、一次 Slot 列表、一次 Item/Revision 列表后内存装配 targets，禁止按 target N+1；
4. **PII**：客户姓名/电话/地址仅经 workorder 模块脱敏后输出；禁止 objectKey / 永久 URL / 原始 GPS；
5. **CLIENT Case**：只读，不返回可用 `DECIDE`；
6. **门禁**：九项 gateChecks；`ALL_TARGETS_DECIDED` / `REJECTED_TARGET_COMPLETE` 为 PENDING（正式以命令校验为准）。

## 已实现

- OpenAPI 1.0.48 FINAL_REVIEW + revision 下载授权
- `DefaultFinalReviewWorkspaceQueryService` + Controller
- `EvidenceCommandService.getRevision` / `authorizeRevisionDownload`
- `WorkOrderMaskedContactView` 脱敏端口
- 测试：`WorkOrderWorkspaceControllerSecurityTest`、`WorkOrderWorkspacePostgresIT`、`WorkOrderMaskedContactTest`

## 明确未实现

见 M352～M355；`aggregateVersion` 响应字段暂投影为 `1`，待 M353 落库。

## 工程证据

- OpenAPI 1.0.48；Flyway 仍 129（本切片无迁移）
- `bash scripts/agent-verify.sh test WorkOrderMaskedContactTest,WorkOrderWorkspaceControllerSecurityTest`
- `bash scripts/agent-verify.sh it WorkOrderWorkspacePostgresIT`

## 验证命令

```bash
bash scripts/agent-verify.sh test WorkOrderMaskedContactTest,WorkOrderWorkspaceControllerSecurityTest
bash scripts/agent-verify.sh it WorkOrderWorkspacePostgresIT
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh client-ts
```
