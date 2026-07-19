---
title: M316 吉利提审出站 stub 与审核回调
status: Implemented
milestone: M316
lastUpdated: 2026-07-19
relatedMilestones: [M297, M298, M311, M314]
---

# M316 吉利提审出站 stub 与审核回调

## 目标

补齐 P4 本地可测面：吉利提审出站 Profile/Connector（本地技术 ACK）与 7.13 核销审核回调入站；真实 Sandbox/OpenAPI 签名仍 BLOCKED_EXTERNAL。

## 范围

- `GeelyOutboundReviewSubmissionProfile`（GEELY CREATE lineage）
- `GeelyOutboundSubmissionConnector`（AES 包装 + LOCAL_ACCEPT 技术 ACK）
- `GeelyInboundReviewCallbackService` → `notify_settlement_audit_result`
- 多 OEM：`requireForRouteRegistration` 未知 mapping 失败关闭；ReviewCase IT 改用 BYD 精确 mapping
- 单元测试 + ReviewCasePostgresIT 回归

## 明确未实现 / BLOCKED_EXTERNAL

- 真实浩瀚 HTTP / OpenAPI SDK 签名；Sandbox 联调；材料附件拉取（7.11.2）

## 验证

```bash
bash scripts/agent-verify.sh test GeelyOutboundSubmissionConnectorTest,OutboundReviewSubmissionProfilesTest
bash scripts/agent-verify.sh it ReviewCasePostgresIT
```
