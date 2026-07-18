# ServiceOS iOS Core

面向 Technician iOS 的无角色 Swift 6 共享基础，当前提供 Token Vault 协议、仅测试用内存实现、服务端
opaque Context 边界、HTTPS URLRequest 构造、强制 clientKind/clientVersion、Problem Details 安全文案与 correlation/trace 诊断。

本模块不包含页面、角色、菜单和数据范围判断；不把内存 Vault 当生产 Token 存储。正式 iOS 工程必须在
后续里程碑提供 Keychain 实现、OIDC PKCE、URLSession transport、日志脱敏和设备级安全测试。

```bash
bash scripts/agent-verify.sh ios-core
```
