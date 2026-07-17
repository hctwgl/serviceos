---
title: M138 Admin BYD 厂端审核回调验收
status: Implemented
lastUpdated: 2026-07-16
---

# M138 Admin BYD 厂端审核回调验收

| ID | 场景 | 证据 | 结果 |
|---|---|---|---|
| M138-01 | 同租户回调配置 | Backend `tenant-local` + adapter grant | PASS |
| M138-02 | Admin 可见 CLIENT 系谱 | 外发详情 `clientReviewCaseId` 链接 | PASS |
| M138-03 | 签名回调成功 | CPIM Sign → message=success | PASS |
| M138-04 | CLIENT 关闭 | Admin 详情 origin=CLIENT status=APPROVED | PASS |
| M138-05 | 持久化证据 | EXTERNAL receipt/decision + COMPLETED Envelope | PASS |

不证明真实 sandbox、REJECTED 回调浏览器路径或入站接单完整链。
