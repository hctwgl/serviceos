---
title: M251 iOS auth/context/error/trace 共享基础验收矩阵
status: Implemented
milestone: M251
lastUpdated: 2026-07-18
---

# M251 iOS auth/context/error/trace 共享基础验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M251-01 | Token 到期 | Actor Vault 到期或进入 skew 后返回 nil 并清除 | Swift async smoke |
| M251-02 | Context 版本变化 | 异步通知宿主清理缓存，不解释 scopeRef | Swift async smoke |
| M251-03 | 正常请求 | HTTPS URL、Bearer、correlation 与宿主 Context Header 正确 | URLRequest assertions |
| M251-04 | Problem/诊断 | 403 固定安全文案；correlation 可定位；不回显 detail | Swift smoke + source review |
| M251-05 | Swift 6 并发边界 | 模块与独立 executable 严格编译、链接、运行 | `agent-verify.sh ios-core` |
| M251-06 | 共享边界 | 源码无 Portal/角色词，内存 Vault 明确非生产 | source gate + docs review |

## 明确未验收

Keychain、OIDC、真实 URLSession 网络、Xcode/真机、日志脱敏落盘、客户端元数据、后台上传与离线运行时。
