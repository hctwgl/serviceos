---
title: M343 REFERENCE_OEM SAMPLE Update/Cancel Mapping 验收矩阵
status: Implemented
milestone: M343
lastUpdated: 2026-07-19
---

# M343 REFERENCE_OEM SAMPLE Update/Cancel Mapping 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M343-01 | create→update | 联系人/地址更新；Mapping 物化 | `ReferenceOemInboundUpdateCancelPostgresIT` |
| M343-02 | update 重放 | replay=true；单工单 | 同上 |
| M343-03 | create→cancel | status=CANCELLED | 同上 |
| M343-04 | cancel 重放 | replay=true | 同上 |
| M343-05 | 既有 CREATE | 仍绿 | `ReferenceOemInboundOrderPostgresIT` |
| M343-06 | 模块边界 | ArchitectureTest | ArchitectureTest |

## 明确不验收

- OpenAPI/Flyway、真实第二车企合同、吉利 Sandbox、Dual/Multi Update 冒烟
