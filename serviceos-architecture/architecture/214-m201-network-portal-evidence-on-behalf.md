---
title: M201 Network Portal 资料代补（onBehalf）
status: Implemented
milestone: M201
lastUpdated: 2026-07-17
relatedMilestones: [M38, M45, M194, M196, M200]
---

# M201 Network Portal 资料代补（onBehalf）

## 目标

在 M200 改派与 M38 资料上传之上，交付 Network Portal 最小可靠「整改代补」纵向切片：
可信网点上下文下为 ACTIVE 师傅代上传 EvidenceRevision，并支持整改 resubmit。

## 范围与非目标

- 范围：
  - ADR-039：Portal begin/finalize on-behalf + correction resubmit；
  - 领域启用受控 `onBehalfOf`（命令级字段）；普通 submit 路径仍拒绝客户端伪造；
  - Core OpenAPI `0.93.0`；Flyway V099 / 101；
  - Page Registry `NETWORK.EVIDENCE.SUPPLEMENT` + catalog `page-registry-v8`；
  - Admin Web Network Portal 代补控件与 E2E spec；
  - PostgreSQL IT、MVC Security、ArchitectureTest。
- 明确不做：
  - 跨网点代补、无整改任意代补、表单字段代改；
  - Visit / 离线工作包 / OCR/GPS 权威；
  - 槽位 `allowOnBehalf` 配置表；
  - 完整 product/03 设计系统与 Consumer Identity。

## 事实源

- ADR-039
- `product/03-network-portal-spec.md` §9（Proposed 产品意图；本 ADR 窄接受 HTTP）
- `product/07-page-action-permission-matrix.md` 网点代补行
- M38 CaptureMetadata；M45 CorrectionCase；M194 `X-Network-Context`

## 设计要点

- Portal 门禁：`evidence.submitOnBehalf` + ACTIVE NetworkMembership + ACTIVE NETWORK 责任；
- 代补触发：任务存在未关闭 CorrectionCase；
- `onBehalfOf` = ACTIVE TECHNICIAN assignee，且为本网点 ACTIVE 师傅成员；
- CaptureMetadata：`uploadedBy`=操作者，`onBehalfOf`/`onBehalfReason` 仅服务端写入；
- resubmit 复用 `CorrectionCaseService.resubmit`（仍要求 `evidence.submit` 或与既有语义一致的授权；Portal 适配器在门禁后委托）。

## 已实现

- [x] ADR-039
- [x] OpenAPI Core `0.93.0`
- [x] `EvidenceCommandService.beginUploadOnBehalf` / `finalizeUploadOnBehalf`
- [x] `NetworkPortalEvidenceService` / Controller
- [x] Flyway V099 `evidence.submitOnBehalf`
- [x] Page Registry v8
- [x] Admin Web + E2E
- [x] PostgresIT / Security MVC / ArchitectureTest

## 明确未实现

- 槽位级 allowOnBehalf 配置；
- 表单代改 / Visit / 离线包；
- Network Portal 完整整改队列产品 UI。

## 工程证据

- OpenAPI：`serviceos-core-v1.yaml` 0.93.0
- IT：`NetworkPortalEvidenceOnBehalfPostgresIT`
- MVC：`NetworkPortalEvidenceControllerSecurityTest`
- E2E：`network-portal-evidence-on-behalf.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,NetworkPortalEvidenceOnBehalfPostgresIT,NetworkPortalEvidenceControllerSecurityTest,CaptureMetadataValidatorTest
./mvnw -pl serviceos-contracts -am test
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
