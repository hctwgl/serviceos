# ServiceOS Frontend

ServiceOS 的唯一 Web Workspace。第一阶段只交付 Admin 黄金链路；Network 与 Technician Web
将在各自重建时迁入。本工程不提供旧路由、旧页面或 Demo 兼容层。

## 本地环境

```bash
nvm use
corepack enable
pnpm install
pnpm product-data:reset
pnpm dev
```

前端日常门禁只包含静态检查、类型检查、纯逻辑单测与构建。产品功能必须连接真实本地后端，
在 Chrome 中人工完成验收；仓库不维护 Web E2E、自动截图或浏览器测试基线。

## 上游工程基线

工程目录和应用分层参考 Vue Vben Admin 5.7.0 `web-antd`，保留 Vue、Vite、Pinia、路由和
Ant Design Vue 技术方向；没有复制其 Demo、Mock、多 UI 适配、多主题或示例业务。

Vue Vben Admin 使用 MIT License：<https://github.com/vbenjs/vue-vben-admin>。
