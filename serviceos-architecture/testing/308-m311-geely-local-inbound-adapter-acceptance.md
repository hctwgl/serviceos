---
title: M311 吉利本地入站适配器验收矩阵
status: Implemented
milestone: M311
lastUpdated: 2026-07-19
---

# M311 吉利本地入站适配器验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| G311-01 | AES round-trip | 加解密一致 | `GeelyAesCipherTest#roundTripsLocalPlaintext` |
| G311-02 | 文档示例密文 | 解密含 installProcessNo | `GeelyAesCipherTest#decryptsProtocolDocumentSampleCiphertext` |
| G311-03 | 7.1 → Canonical | client/brand/省市区/联系人 | `GeelyCreateOrderMapperTest` |
| G311-04 | HTTP 建单 + 重放 | code=0；工单 1 条；connectorVersion | `GeelyInboundCreateOrderPostgresIT` |
| G311-05 | 外部阻塞登记 | Sandbox/签名 BLOCKED_EXTERNAL | `05-geely-haohan-v13-adapter-contract.md` |
