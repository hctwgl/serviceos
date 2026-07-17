---
title: M141 Admin 入站同单表单/资料/审核/外发 E2E
status: Implemented
milestone: M141
lastUpdated: 2026-07-17
---

# M141 Admin 入站同单表单/资料/审核/外发 E2E

## 1. 范围

承接 M140，在**同一 CPIM 入站工单**上证明：

```text
CPIM CREATE_WORK_ORDER（Canonical business_key = BYD:INSTALL:{orderCode}）
→ Outbox 激活 ACTIVE + HUMAN Task（formRef + PILOT_SURVEY EvidenceSlot）
→ Admin assign / claim / start
→ propose → confirm → check-in → check-out
→ FormSubmission VALIDATED → Evidence Snapshot
→ INTERNAL ReviewCase APPROVED
→ BYD 提审外发 ACKNOWLEDGED + CLIENT Case
→ 厂端回调 CLIENT:APPROVED
→ 双输入 complete → Task/Workflow COMPLETED、WorkOrder FULFILLED
```

ServiceAssignment 仍由本地夹具注入；不宣称 Admin 派单 HTTP，不宣称完整 `ADMIN-PILOT-09`
（派单 HTTP、整改分支、专用入站队列页、真实 sandbox 仍未证明）。

## 2. 实现要点

1. 入站 Canonical `business_key` 统一为 `BYD:INSTALL:{orderCode}`，与出站提审契约对齐；HTTP
   响应 `orderCode` 仍返回裸 CPIM 外部单号；
2. `ADMIN-PILOT` WORKFLOW USER_TASK 增加 `formRef=admin.pilot-inbound-form`；Bundle 增加
   FORM/EVIDENCE（`stage=PILOT_SURVEY`），digest 为定义 UTF-8 SHA-256；
3. Playwright 在入站动态工单上贯通表单/资料/审核/外发/回调/完结；
4. 冒烟 SQL 断言 FULFILLED、系谱前缀、formRef、Submission/Snapshot、INTERNAL/Outbound/CLIENT。

## 3. 明确未实现

- Admin ServiceAssignment / 派单 HTTP；
- 同单整改驳回/补传分支（仍由独立夹具证明）；
- 专用 `/integration/inbound` 队列页；
- 真实 sandbox / 生产对象存储 / 专业扫描；
- 完整 `ADMIN-PILOT-09`。
