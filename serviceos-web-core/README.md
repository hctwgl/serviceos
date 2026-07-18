# ServiceOS Web Core

面向独立 Web Portal 的无角色共享基础包，提供：

- 只驻留内存且到期失败关闭的 Access Token 容器；
- 只保存服务端 opaque `contextId/contextVersion` 的上下文边界；
- Bearer、correlation/trace 诊断元数据与可注入 Fetch 客户端；
- Problem Details 解析、结构化诊断和不回显后端敏感 detail 的固定用户文案。

本包不实现 OIDC Provider/BFF，不持久化 Token，不解释任何 Portal/角色/菜单/tenant/project/network
范围，也不替代服务端 Capability 与 Scope 授权。宿主应用负责登录跳转、上下文 API、缓存实现和页面导航。

验证：

```bash
bash scripts/agent-verify.sh web-core
```
