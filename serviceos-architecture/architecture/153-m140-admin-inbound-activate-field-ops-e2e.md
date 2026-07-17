---
title: M140 Admin 入站激活与同单预约上门 E2E
status: Implemented
milestone: M140
lastUpdated: 2026-07-17
---

# M140 Admin 入站激活与同单预约上门 E2E

## 1. 范围

承接 M139，在**同一入站工单**上证明：

```text
CPIM CREATE_WORK_ORDER
→ Outbox workorder.received 启动可解析 WORKFLOW
→ WorkOrder ACTIVE + HUMAN Task READY
→ Admin assign-candidates / claim / start
→ propose → confirm → check-in → check-out
```

ServiceAssignment 仍由本地夹具注入（Admin 派单 HTTP 未建立）；不宣称完整 `ADMIN-PILOT-09`，
也不宣称同单表单/资料/审核/外发贯通。

## 2. 实现要点

1. `ADMIN-PILOT` WORKFLOW 资产改为可被 `WorkflowDefinitionParser` 解析的线性 USER_TASK；
   本地夹具若已写入旧占位定义，仅在种子脚本内短暂关闭配置不可变触发器后替换（禁止生产用法）；
2. 冒烟脚本签名 POST `/install-orders`，轮询至 ACTIVE + Task，再注入 NETWORK/TECHNICIAN SA；
3. Playwright 在该工单工作区完成领取/启动，并在任务详情完成预约上门；
4. SQL 断言 Envelope/Canonical/Workflow/Task/Appointment/Visit/审计闭环。

## 3. 明确未实现

- Admin ServiceAssignment / 派单 HTTP；
- 同单表单/资料/审核/整改/外发/厂端回调贯通；
- 专用 `/integration/inbound` 队列页；
- 真实 sandbox；
- 完整 `ADMIN-PILOT-09`。
