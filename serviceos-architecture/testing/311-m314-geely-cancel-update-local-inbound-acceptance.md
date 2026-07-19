---
title: M314 吉利取消/更新本地入站验收矩阵
status: Implemented
milestone: M314
lastUpdated: 2026-07-19
---

# M314 吉利取消/更新本地入站验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| G314-01 | create→update→close | 联系人/地址更新后 CANCELLED | `GeelyInboundCancelUpdatePostgresIT` |
| G314-02 | VIN 缺省建单 | 派生占位 VIN，建单成功 | 同上 + mapper 行为 |
| G314-03 | Sandbox 阻塞登记 | 文档标明 BLOCKED_EXTERNAL | 实现文档 / 05-geely 契约 |
