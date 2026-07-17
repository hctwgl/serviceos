---
title: M138 Admin BYD 厂端审核回调联调 E2E
status: Implemented
milestone: M138
lastUpdated: 2026-07-16
---

# M138 Admin BYD 厂端审核回调联调 E2E

## 1. 范围

承接 M137 ACKNOWLEDGED + CLIENT ReviewCase，在同一 Admin 试点冒烟中证明：

```text
ACKNOWLEDGED + CLIENT OPEN
→ CPIM 签名厂端回调（result=1）
→ EXTERNAL Receipt + EXTERNAL APPROVED
→ Admin 打开 CLIENT 案例可见 APPROVED
```

本地签名使用试点 app-key/secret；不宣称真实 sandbox。

## 2. 实现要点

1. 冒烟 Backend 以 `SERVICEOS_BYD_CPIM_TENANT_ID=tenant-local` 启动，使回调与交付同租户；
2. Admin 外发详情展示 `clientReviewCaseId` / `reviewRouteId` 与 CLIENT 链接；
3. Playwright 在 ACK 后按 V7.3.1 签名规则 POST `/integrations/byd/cpim/v7.3.1/review-results`；
4. SQL 断言 CLIENT APPROVED、EXTERNAL decision、COMPLETED Envelope。

## 3. 明确未实现

- 真实 sandbox / 厂端联调环境；
- 回调 REJECTED + 客服协调 Task 浏览器证明；
- 入站 CREATE_WORK_ORDER 接单浏览器链；
- 完整 `ADMIN-PILOT-09`。
