---
title: M139 Admin BYD 入站 CREATE_WORK_ORDER 接单 E2E
status: Implemented
milestone: M139
lastUpdated: 2026-07-17
---

# M139 Admin BYD 入站 CREATE_WORK_ORDER 接单 E2E

## 1. 范围

承接 M138，在同一 Admin 试点冒烟中证明外部入站接单边：

```text
CPIM 签名 POST /install-orders（唯一 orderCode）
→ Envelope/Canonical COMPLETED + WorkOrder RECEIVED
→ Admin 工单目录（status=RECEIVED）打开工作区
→ 概览可见外部单号与 RECEIVED
→ INTEGRATION 区块可见 CREATE_WORK_ORDER / COMPLETED / WORK_ORDER / ACCEPTED
```

本地签名使用试点 app-key/secret；冒烟 Backend 绑定 `tenant-local` + `ADMIN-PILOT`。
不宣称真实 sandbox，不宣称完整 `ADMIN-PILOT-09`。

## 2. 实现要点

1. `verify-admin-smoke.sh` 启动 Backend 时设置 `SERVICEOS_BYD_CPIM_PROJECT_CODE=ADMIN-PILOT`，
   使入站配置解析命中试点项目与 Bundle；
2. 每轮生成唯一 `ADMIN_PILOT_INBOUND_ORDER_CODE`，由 Playwright 按 V7.3.1 签名规则 POST
   `/api/v1/integrations/byd/cpim/v7.3.1/install-orders`；
3. Admin 以真实 Keycloak 登录后按 `RECEIVED` 过滤并打开工作区，加载 INTEGRATION 区块；
4. SQL 断言工单 `RECEIVED`、COMPLETED Envelope、ACCEPTED Canonical、审计
   `INBOUND_MESSAGE_PROCESSED` 与 `integration.canonical-message-processed` Outbox。

## 3. 明确未实现

- 入站工单激活、Workflow/Task 创建与派单 Admin HTTP；
- 同一入站工单上的预约→上门→表单/资料→审核→外发完整链；
- 专用 `/integration/inbound` 队列页；
- 真实 sandbox / 生产厂端联调；
- 完整 `ADMIN-PILOT-09`。
