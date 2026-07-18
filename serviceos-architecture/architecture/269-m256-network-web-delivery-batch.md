---
title: M256 独立 Network Web 完整交付批次
status: Implemented
milestone: M256
lastUpdated: 2026-07-18
relatedMilestones: [M194, M242, M250, M253, M255]
---

# M256 独立 Network Web 完整交付批次

## 1. 交付范围

M256 按“一个交付批次一个里程碑、多个内部提交、一次最终 L3”闭合 Track B，不把身份、页面或部署文件
拆成多个技术里程碑：

- 独立应用实际消费 `@serviceos/web-core`，固定发送 `NETWORK_WEB` 与有界客户端版本；
- 支持可配置 OIDC Authorization Code + PKCE，生产 issuer 强制 HTTPS；Access Token 仅保存在内存，
  sessionStorage 只保存一次性 state、verifier 和受限返回路径；
- 从 `/me/contexts` 只选择服务端返回的 NETWORK Context，再以同一 contextVersion 加载 Capability 与导航；
- 迁移 M194～M242 的工作台、工单、限定工作区、任务、预约/联系、指派/改派、资料代补、师傅、资质、
  产能、整改和异常页面；业务请求继续携带 `X-Network-Context` 并由服务端独立鉴权；
- 原 Admin 46 个 Network Playwright 文件迁移为独立应用 75 条回归，并增加 OIDC/元数据/A-B 网点切换/
  伪造 Context 负向规格，总计 76 条；
- Admin 删除正式 `/network-portal/*` 路由、14 个页面和业务 API，仅保留 M188 Context/导航诊断入口，
  菜单改为可配置的独立应用外链；
- 提供 Node 构建 + unprivileged Nginx 独立镜像、SPA fallback、运行时 API 反代与 `/healthz`。

## 2. 安全与隔离

- 客户端不能通过 query/body 自报 networkId；Context 必须命中本轮 `/me/contexts` 返回集合；
- 切换 Context 后重新加载 Capability、导航与业务投影，不复用上一网点页面数据；
- 导航/Capability 仅决定呈现，不能替代后端 API 授权；伪造 Context 在客户端和真实 PostgreSQL 服务端均失败关闭；
- Network DTO、页面与门禁不暴露 price/pricing/settlement/quote/serviceFee；跨网点隔离由
  `PortalContextPostgresIT`、`NetworkPortalReadPostgresIT` 和既有各 Network PostgreSQL IT 证明；
- 生产构建不接受手工 Token，容器和构建参数不包含 client secret。

## 3. 验证证据

- `bash scripts/agent-verify.sh network-web`：不可变安装、生产构建、76 条 Chrome E2E、环境/会话正负探针、
  价格字段与 Admin 旧路由回流门禁；
- `PortalContextPostgresIT,NetworkPortalReadPostgresIT,NetworkPortalControllerSecurityTest`：真实 PostgreSQL
  Context/跨网点隔离与 HTTP 安全专项通过；
- Admin production build 通过，证明删除 Network 正式路由后仍可独立构建；
- `docker build -f serviceos-network-web/Dockerfile ...` 成功，容器 `/healthz` 与
  `/network-portal/workbench` SPA 深链实跑通过；
- Core OpenAPI 仍为 1.0.21，Flyway 仍为 100/102；本批次不修改 HTTP/事件/数据库契约。

## 4. 明确未实现

`NETWORK.NOTIFICATION`、Portal ACK/resolve/decide、资质 decide、产能申请、价格/结算、共享 UI Preference、
离线工作包、正式集群发布与生产 IdP 联调仍不在 M256 范围。Track B 仓库内独立产品边界已闭合，下一交付
批次进入 Track C Technician H5 独立化。
