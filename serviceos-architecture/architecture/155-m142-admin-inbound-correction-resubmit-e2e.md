---
title: M142 Admin 入站同单整改补传复审外发 E2E
status: Implemented
milestone: M142
lastUpdated: 2026-07-17
---

# M142 Admin 入站同单整改补传复审外发 E2E

## 1. 范围

承接 M141，在**同一 CPIM 入站工单**上证明整改分支：

```text
CPIM CREATE_WORK_ORDER → 激活 + HUMAN Task（SA 仍为本地夹具）
→ assign / claim / start
→ propose → confirm → check-in → check-out
→ FormSubmission + 首轮 Evidence Snapshot
→ INTERNAL REJECTED → CorrectionCase IN_PROGRESS
→ 同 Item 补传 Revision + 第二轮 Snapshot
→ resubmit → close
→ 新 INTERNAL ReviewCase APPROVED
→ BYD 提审 ACKNOWLEDGED + 厂端回调 CLIENT:APPROVED
→ 双输入 complete → FULFILLED
```

不宣称 Admin 派单 HTTP，不宣称完整 `ADMIN-PILOT-09`。

## 2. 实现要点

1. 复用已 Implemented 的 Review/Correction/Outbound 命令与 Admin 页面（M135 + M141）；
2. Playwright 入站动态工单由直接 APPROVED 改为 REJECTED→补传复审后再外发完结；
3. 冒烟 SQL 断言双 Snapshot、REJECTED Case、CLOSED Correction、APPROVED 复审、Outbound/CLIENT 与 FULFILLED。

## 3. 明确未实现

- Admin ServiceAssignment / 派单 HTTP；
- 专用 `/integration/inbound` 队列页；
- 真实 sandbox / 生产对象存储 / 专业扫描；
- 完整 `ADMIN-PILOT-09`。
