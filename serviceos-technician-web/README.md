# ServiceOS Technician H5

独立师傅在线参考应用。当前包含 OIDC Authorization Code + PKCE、`/me` Technician Context、
Capability、服务端导航，以及从 Admin 迁移的 Feed、日程、同步摘要、我的和当前责任任务详情。

```bash
npm ci
npm run build
npm run test:e2e
```

生产 API 地址及 OIDC issuer 必须使用同源路径/HTTPS；Access Token 只保存在内存，刷新页面需要重新登录；
`clientKind` 固定为 `TECHNICIAN_WEB`，不能由环境变量改写，也不参与授权。所有业务请求继续携带
`X-Technician-Context`，服务端按当前责任独立鉴权。

H5 是在线契约、产品走查和自动化回归的参考实现，不承诺原生级定位、后台可靠上传、杀进程恢复、
长期本地敏感数据保存或完整离线能力。M262 已在当前任务详情加入用户主动触发的一次性浏览器定位签到和
无法施工中断；浏览器定位不等于原生设备可信度。联系、表单和 Evidence 仍为只读摘要；没有真实
`operationRefs` 时不会开放签退或伪造现场完成。

容器必须从仓库根目录构建，以便安装同仓 `@serviceos/web-core`：

```bash
docker build -f serviceos-technician-web/Dockerfile \
  --build-arg VITE_SERVICEOS_CLIENT_VERSION=1.0.0 \
  --build-arg VITE_OIDC_ENABLED=true \
  --build-arg VITE_OIDC_ISSUER=https://identity.example/realms/serviceos \
  --build-arg VITE_OIDC_CLIENT_ID=serviceos-technician-web \
  -t serviceos-technician-web:1.0.0 .
```

运行时用 `SERVICEOS_API_UPSTREAM` 指定后端反代目标；容器监听 8080 并提供 `/healthz`。构建参数不含
client secret，OIDC 公共客户端使用 PKCE。
