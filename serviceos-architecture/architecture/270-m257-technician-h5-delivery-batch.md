---
title: M257 独立 Technician H5 交付批次
status: Implemented
milestone: M257
lastUpdated: 2026-07-18
relatedMilestones: [M195, M218, M219, M243, M246, M250, M253]
---

# M257 独立 Technician H5 交付批次

## 1. 交付范围

M257 按“一个交付批次一个里程碑、多个内部提交、一次最终 L3”闭合 Track C 的独立应用迁移边界：

- 建立独立、移动优先且可部署的 `serviceos-technician-web`，实际消费 `@serviceos/web-core`；
- 使用 OIDC Authorization Code + PKCE，生产 issuer 强制 HTTPS，Access Token 只保存在内存；
- 只从 `/me/contexts` 选择服务端返回的 TECHNICIAN Context，再以同一 contextVersion 加载 Capability 与导航；
- 迁移 M195、M218～M219、M243～M246 的 Feed、日程、同步摘要、我的和当前责任任务详情；
- 任务详情保留已接受的预约、联系、Visit 和表单提交只读安全摘要，不把它们误报为写闭环；
- 迁移原 Admin 6 条回归，增加 PKCE/元数据/tombstone/A-B Context 隔离和 409/5xx 恢复，共 8 条 Playwright；
- 双运行通过后删除 Admin 正式 `/technician-portal/*` 路由、6 个页面、业务 API 和旧 E2E；M188 诊断入口保留；
- 提供 Node 构建 + unprivileged Nginx 镜像、SPA fallback、运行时 API 反代与 `/healthz`。

## 2. 安全、责任与状态语义

- 客户端不能自报 networkId；业务请求只携带本轮服务端返回的 `X-Technician-Context`；
- 切换 Context 时先销毁旧页面，再加载新 Capability、导航和投影，防止旧师傅/网点数据残留；
- tombstone 明确展示失效原因；导航和 Capability 只决定呈现，当前责任仍由服务端实时校验；
- 403/404、409/412 和 5xx 使用固定安全文案，后端 detail 不直接回显；
- 页面明确 H5 不承诺原生级定位、后台上传、杀进程恢复或完整离线可靠性；
- 本地、上传、服务器接收和业务接受状态不得合并成一个“成功”。本里程碑未新增写状态，因此不伪造这些状态。

## 3. 验证证据

- `bash scripts/agent-verify.sh technician-web`：不可变安装、生产构建、8 条 Chrome E2E、环境/会话正负探针、
  价格字段与 Admin 旧路由回流门禁；
- `TechnicianPortalFeedPostgresIT,TechnicianPortalControllerSecurityTest`：真实 PostgreSQL 当前责任、
  tombstone、Context 和 HTTP 安全专项通过；
- Admin production build 通过，证明移除 Technician 正式路由后仍可独立构建；
- 独立镜像构建上下文 5.42kB，容器 `/healthz` 与 `/technician-portal/task-feed` 深链实跑 200；
- Core OpenAPI 仍为 1.0.21，Flyway 仍为 100/102；本批次不修改 HTTP、事件或数据库契约。

## 4. 明确未实现

联系/预约写、Visit 写、动态表单/草稿、Evidence 上传、提交/整改在线闭环属于 Track E；SwiftUI/Xcode、
Keychain、真机和 TestFlight 属于 Track D；离线工作包、后台上传和冲突恢复属于 Track F。生产 IdP/集群发布、
MESSAGE/PROFILE 正式页面和通知也不在 M257 范围。下一交付批次进入 Track D Technician iOS 工程基础。
