---
title: M143 Admin 试点 SPI ServiceAssignment 种子
status: Implemented
milestone: M143
lastUpdated: 2026-07-17
---

# M143 Admin 试点 SPI ServiceAssignment 种子

## 1. 范围

承接 M142，去掉冒烟脚本对 `dsp_service_assignment` 的 SQL 直插，改为经已 Implemented 的
Dispatch SPI 编排注入 Visit 所需责任：

```text
CapacityAuthorityService.configure（NETWORK / TECHNICIAN）
→ ServiceAssignmentService.prepare → confirmTaskPrepared → activate → complete
→ ACTIVE NETWORK + TECHNICIAN + CONFIRMED reservation + COMPLETED saga
```

覆盖 field-ops 夹具工单与 CPIM 入站动态工单两条路径。不暴露 Admin 派单 HTTP，不宣称完整
`ADMIN-PILOT-09`。

## 2. 实现要点

1. `AdminPilotLiveServiceAssignmentSeeder`：仅在
   `-Dserviceos.admin.pilot.seed=true` 时启用的 NONE Web SpringBootTest，连接本地试点库并关闭
   第二套 Outbox/Task 调度；
2. `seed-admin-pilot-assignment.sh` + `verify-admin-smoke.sh` 在 Playwright 前调用并 SQL 断言
   `1:1:2:2`（ACTIVE NETWORK/TECHNICIAN、两条 CONFIRMED reservation、两条 COMPLETED saga）；
3. 种子主体 `admin-pilot-sa-seeder` 仅持有 `dispatch.capacity.configure` 与
   `dispatch.assignment.manage`；不扩大本地项目管理员写派单能力；
4. 初派 Task 握手使用合成 `preparedTaskAssignmentId` / Guard（与 M24 IT 一致），不发明评分、
   硬过滤或 ServiceNetwork 生命周期。

## 3. 明确未实现

- Admin ServiceAssignment / 派单 HTTP（OpenAPI、Controller、Admin UI）；
- 完整 `ADMIN-PILOT-09`（接单→**Admin 派单**→…→完结）；
- 专用入站队列页、真实 sandbox、生产对象存储/专业扫描。

## 4. 删除条件

当 Accepted 的 Manual Assign HTTP 落地且 Admin 冒烟改为经该表面创建 ServiceAssignment 后，删除
本种子入口与 `AdminPilotLiveServiceAssignmentSeeder`。
