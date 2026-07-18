---
title: M255 独立 Network Web AppShell 与环境基础验收矩阵
status: Implemented
milestone: M255
lastUpdated: 2026-07-18
---

# M255 独立 Network Web AppShell 与环境基础验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M255-01 | 独立依赖 | 自有 package/lock，可用 `npm ci` 不可变安装 | network-web gate |
| M255-02 | 生产构建 | vue-tsc 与 Vite build 通过并产出 dist | build output |
| M255-03 | 客户端身份 | clientKind 固定 `NETWORK_WEB`，版本合法 | environment probe |
| M255-04 | 环境隔离 | 未知环境失败，不静默映射到 production | negative probe |
| M255-05 | 传输安全 | 同源路径/HTTPS 允许，HTTP API 拒绝 | negative probe |
| M255-06 | 交付边界 | Shell 明示登录、上下文、导航和业务页尚未接入 | UI/source review |

## 明确未验收

OIDC、`/me`、Network Context、Capability/导航、业务路由、API、数据隔离、E2E、部署和旧路由移除。
