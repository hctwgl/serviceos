---
title: M257 独立 Technician H5 交付批次验收矩阵
status: Implemented
milestone: M257
lastUpdated: 2026-07-18
---

# M257 独立 Technician H5 交付批次验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M257-01 | 独立身份 | OIDC Code+PKCE；生产 HTTPS issuer；无 client secret | auth source + E2E |
| M257-02 | Token 存储 | Access Token 仅内存，localStorage 无 Token | source gate + Playwright |
| M257-03 | Context 来源 | 只接受 `/me/contexts` 返回的 TECHNICIAN Context | session probe + forged E2E |
| M257-04 | Capability/导航 | 同 contextVersion 加载且不替代业务授权 | session probe + E2E |
| M257-05 | 客户端元数据 | 固定 `TECHNICIAN_WEB` 与有界版本 | header assertion |
| M257-06 | 页面迁移 | Feed/Schedule/Sync/Me/Task Detail 可用 | 6 migrated E2E |
| M257-07 | tombstone/深链 | 责任失效原因可见，登录返回原深链 | isolation E2E |
| M257-08 | Context 切换 | A/B 分别取数，上一 Context 行立即消失 | isolation E2E |
| M257-09 | 服务端责任 | 当前责任、伪造 Context 与 HTTP 安全失败关闭 | PostgreSQL IT + MVC security |
| M257-10 | 异常恢复 | 409/5xx 固定可行动文案，不回显 detail | recovery E2E |
| M257-11 | H5 能力边界 | 不承诺原生定位、后台上传、杀进程恢复、完整离线 | source gate + UX assertion |
| M257-12 | Admin 退役 | 无正式 Technician route/page/API；诊断 stub 保留 | source gate + Admin build |
| M257-13 | 独立部署 | 镜像可构建，healthz/SPA 深链实跑 200 | docker build/run/curl |
| M257-14 | 契约/迁移 | OpenAPI 1.0.21、Flyway 100/102 不变 | contract/migration gates |
| M257-15 | 批次门禁 | 多内部提交后冻结候选只执行一次最终 L3 | final verify log |

## 明确未验收

Track D iOS 工程、Track E 在线写闭环、Track F 离线与后台上传、生产 IdP/集群发布、消息/通知。
