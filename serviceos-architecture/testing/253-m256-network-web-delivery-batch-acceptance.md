---
title: M256 独立 Network Web 完整交付批次验收矩阵
status: Implemented
milestone: M256
lastUpdated: 2026-07-18
---

# M256 独立 Network Web 完整交付批次验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M256-01 | 独立身份 | OIDC Code+PKCE；生产 HTTPS issuer；无 client secret | auth source + E2E |
| M256-02 | Token 存储 | Access Token 仅内存，localStorage 无 Token | source gate + Playwright |
| M256-03 | Context 来源 | 只接受 `/me/contexts` 返回的 NETWORK Context | session probe + forged E2E |
| M256-04 | Capability/导航 | 同 contextVersion 加载并只用于呈现 | session probe + E2E |
| M256-05 | 客户端元数据 | 请求固定 `NETWORK_WEB` 与有界版本 | header assertion |
| M256-06 | 业务迁移 | M194～M242 工作台/队列/工作区/协作/目录页面均可用 | 75 migrated E2E |
| M256-07 | Context 切换 | A/B 网点分别重新取数，不残留上一网点值 | isolation E2E |
| M256-08 | 服务端隔离 | 伪造/跨网点读写失败关闭 | PostgreSQL IT + MVC security |
| M256-09 | 价格隔离 | Network Client 不出现价格/结算字段 | source gate |
| M256-10 | Admin 退役 | 无正式 Network route/page/API；诊断 stub 保留 | source gate + Admin build |
| M256-11 | 回归迁移 | 独立应用 76/76 Playwright 通过 | `npm run test:e2e` |
| M256-12 | 独立构建 | Network 与删除路由后的 Admin 均 production build | build output |
| M256-13 | 独立部署 | 镜像可构建，unprivileged Nginx healthz/SPA fallback 可运行 | docker build/run/curl |
| M256-14 | 契约/迁移 | OpenAPI 1.0.21、Flyway 100/102 不变 | contract/migration gates |
| M256-15 | 批次门禁 | 多内部提交后仅冻结候选执行一次最终 L3 | final verify log |

## 明确未验收

正式生产 IdP/集群发布、Network notifications、Portal ACK/resolve/decide、价格结算、离线能力和 Track C～G。
