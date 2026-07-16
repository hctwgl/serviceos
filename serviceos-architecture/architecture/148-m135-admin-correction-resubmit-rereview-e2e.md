---
title: M135 Admin 正常整改补传 / 关闭 / 复审写链路 E2E
status: Implemented
milestone: M135
lastUpdated: 2026-07-16
---

# M135 Admin 正常整改补传 / 关闭 / 复审写链路 E2E

## 1. 批准边界

承接 M134 Admin 试点局部读写基线与已 Implemented 的 M45/M47/M113/M116～M121 能力，在真实
Keycloak / Backend / PostgreSQL / Chrome PR 阻断门禁中证明**正常整改主路径**（非 WAIVE）：

```text
REJECTED
→ CorrectionCase(IN_PROGRESS) + evidence.correction Task
→ 源 Task 同 Item 追加补传 Revision + 新 TASK_SUBMISSION Snapshot
→ resubmit → RESUBMITTED
→ close → CLOSED
→ 新 INTERNAL ReviewCase（绑定补传 Snapshot）→ APPROVED
→ FormSubmission + 补传 Snapshot 双引用 complete
→ Outbox/Inbox → WorkOrder FULFILLED
```

不扩大业务状态机，不宣称完整 `ADMIN-PILOT-09`（接单→派单→预约→上门→外发）。

## 2. 实现范围

1. Admin 资料上传在槽位已有 EvidenceItem 时传入 `evidenceItemId`，于同一 Item 追加 Revision，
   避免 `maxCount=1` 夹具阻断正常补传；
2. 追加 Revision 后剔除同 Item 旧成员 ID，使补传 Snapshot 只冻结本轮 VALIDATED Revision；
3. `verify-admin-smoke.sh` 增加第四套每轮新建 UUID 夹具
   （`ADMIN_PILOT_RESUBMIT_*`），并在 Playwright 后以 SQL 断言 Case/补传轮次/复审/
   Task/WorkOrder/审计/Inbox；
4. Playwright 覆盖授权队列定位整改 Case、补传 Snapshot 手工填入 resubmit、close、
   复审 APPROVED 与双引用 complete；
5. 无 uuidgen 环境使用 python3 生成夹具 UUID，保持 CI/云环境可重复。

## 3. 明确未实现

- 从 BYD 入站接单开始的完整履约链；
- 网点 ServiceAssignment Admin 写表面；
- 预约/上门写链路 E2E（本地 RoleGrant 尚缺 propose/checkIn 等写能力）；
- 外部提审 OutboundDelivery + 回调同一浏览器链；
- 正式企业 OIDC/BFF、生产对象存储/专业扫描、SavedView、设计系统。

## 4. 工程证据

- `serviceos-admin-web/src/components/TaskFormsEvidencePanel.vue`
- `serviceos-admin-web/tests/e2e/admin-pilot-smoke.spec.ts`
- `serviceos-deploy/admin-pilot/verify-admin-smoke.sh`
- `testing/132-m135-admin-correction-resubmit-rereview-acceptance.md`
- `docs/admin-pilot-readiness-baseline.md` / `testing/admin-pilot-readiness-acceptance.md`
