---
title: M145 Admin 入站 Envelope/Canonical 详情深链
status: Implemented
milestone: M145
lastUpdated: 2026-07-17
---

# M145 Admin 入站 Envelope/Canonical 详情深链

## 1. 范围

承接 M144，在已 Implemented 的入站只读 GET 上补齐 Admin 运营可见性：

```text
工单工作区 INTEGRATION
→ 深链 /integration/inbound/{envelopeId}
→ GET /inbound-envelopes/{id} + GET /canonical-messages/{id}
→ 展示安全摘要（无原文/签名/凭据）
```

不建立专用入站队列页，不新增列表 API。

## 2. 实现要点

1. Admin `InboundEnvelopeDetailPage` 复用外发详情布局；可选加载 Canonical；
2. 工作区 INTEGRATION 区块解析 `inboundEnvelopes[]` 生成 RouterLink；
3. Playwright 入站用例点击深链并断言 Envelope/Canonical/`BYD:INSTALL:`。

## 3. 明确未实现

- `GET /inbound-envelopes` 授权列表与 `/integration/inbound` 队列索引（需另接受 API-06 §6）；
- 原文下载、验签值暴露、入站重放/人工接管 UI；
- 真实 sandbox。
