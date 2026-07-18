---
title: M250 Web auth/context/error/trace 共享基础验收矩阵
status: Implemented
milestone: M250
lastUpdated: 2026-07-18
---

# M250 Web auth/context/error/trace 共享基础验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M250-01 | Token 保存/到期 | 仅内存；到期和 skew 内失败关闭清除 | Node test |
| M250-02 | Context 版本变化 | 通知宿主清理缓存，不解释 scopeRef | Node test |
| M250-03 | 正常 HTTP | Bearer/correlation/宿主 context header；返回 ETag/诊断 | mock Fetch test |
| M250-04 | 401 Problem Details | 保留 errorCode/correlation；触发重认证；用户文案不回显 detail | negative Node test |
| M250-05 | 独立消费 | npm tarball 可安装并导入公开 API | temporary consumer |
| M250-06 | 共享边界 | src 无 Portal/角色词，Admin production build 不回归 | source gate + Vite build |

## 明确未验收

生产 OIDC/BFF、独立 App 实际接入、刷新流程、浏览器安全 E2E、iOS 对齐、客户端元数据和制品发布。
