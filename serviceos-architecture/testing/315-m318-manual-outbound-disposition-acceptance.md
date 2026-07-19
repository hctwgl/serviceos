---
title: M318 人工确认/放弃验收矩阵
status: Implemented
milestone: M318
lastUpdated: 2026-07-19
---

# M318 人工确认/放弃验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| D318-01 | MANUAL_CONFIRMED | disposition 落库；status 仍 UNKNOWN；无 CLIENT Case | `ManualDispositionPostgresIT#confirmsUnknownDeliveryWithoutClientCase` |
| D318-02 | ABANDONED | disposition=ABANDONED；status UNKNOWN | `ManualDispositionPostgresIT#abandonsUnknownDelivery` |
| D318-03 | 确认缺证据 | VALIDATION_FAILED | `ManualDispositionPostgresIT#failsClosedWithoutEvidenceForManualConfirmed` |
| D318-04 | 匿名 HTTP | 401 | `OutboundDeliveryControllerSecurityTest` |
| D318-05 | 契约 | OpenAPI 1.0.42 兼容 | `agent-verify.sh contracts` |
