---
title: M145 Admin 入站 Envelope/Canonical 详情深链验收
status: Implemented
lastUpdated: 2026-07-17
---

# M145 Admin 入站 Envelope/Canonical 详情深链验收

| ID | 场景 | 证据 | 结果 |
|---|---|---|---|
| M145-01 | 工作区深链可见 | INTEGRATION 区块出现 CREATE_WORK_ORDER 链接 | PASS |
| M145-02 | Envelope 详情 | GET `/inbound-envelopes/{id}` → 页面展示 messageType/status/digest | PASS |
| M145-03 | Canonical 详情 | GET `/canonical-messages/{id}` → `BYD:INSTALL:` businessKey | PASS |
| M145-04 | 回链工单 | 详情页「工单工作区」回到 ACTIVE 入站工单 | PASS |

不证明专用入站队列、原文下载或真实 sandbox。
