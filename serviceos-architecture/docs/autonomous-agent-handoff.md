---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **状态**：**PARTIAL** — M378～M383 主能力持续推进；完整 §四十 收口未闭合
- OpenAPI **1.0.61** / Flyway **138** / ADR-091
- PR：https://github.com/hctwgl/serviceos/pull/204

## 本轮追加

- 正式建单无 Profile **失败关闭**（入站 IT 已种子 Profile）
- 发布链 `effective_to` 可关闭（V138），v1/v2 隔离 + 暂停拒单 IT
- `TaskAllowedActions.blockedActions` + 中文阻塞原因
- 工单详情「配置来源」+ `/configuration-snapshot` 页

## 仍未闭合

1. 真实 OIDC + 全链路 Playwright（发布→建单 A/B）
2. a11y/视觉全量
3. `@serviceos/core-client` 替换 Admin 薄封装
4. 表单/资料缺项级阻塞原因（当前为状态/权限/责任层）
