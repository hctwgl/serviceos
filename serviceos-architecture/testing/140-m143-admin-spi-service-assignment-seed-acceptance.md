---
title: M143 Admin 试点 SPI ServiceAssignment 种子验收
status: Implemented
lastUpdated: 2026-07-17
---

# M143 Admin 试点 SPI ServiceAssignment 种子验收

| ID | 场景 | 证据 | 结果 |
|---|---|---|---|
| M143-01 | field-ops 夹具工单经 SPI 注入 SA | `seed-admin-pilot-assignment.sh` + SQL `1:1:2:2` | PASS |
| M143-02 | 入站动态工单经 SPI 注入 SA | 同上；承接 M140/M142 Playwright 预约上门 | PASS |
| M143-03 | 容量/Saga 事实存在 | CONFIRMED reservation ×2 + COMPLETED saga ×2 | PASS |
| M143-04 | 默认 CI 不执行种子 | `@EnabledIfSystemProperty`；无 seed 属性时跳过 | PASS |

不证明 Admin 派单 HTTP、真实 sandbox 或完整 `ADMIN-PILOT-09`。
