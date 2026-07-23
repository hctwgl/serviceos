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

工程固定采用 Vue Vben Admin 5.7.0 `web-antd` 作为真实运行底座，直接复用其布局、导航、页面容器、
主题变量和 Ant Design Vue 适配，不再自行重写一套仿 Vben 应用壳。删除 Demo、Mock、其他 UI
应用、多主题、暗色模式、偏好中心和示例业务；A+ 金标仍是 ServiceOS 页面布局、信息层级和业务视觉的验收事实源。

Vue Vben Admin 使用 MIT License：<https://github.com/vbenjs/vue-vben-admin>。
