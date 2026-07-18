# ServiceOS Technician iOS Foundation

Technician iOS 的仓库内安全基础，当前提供：

- Local/Development/Test/Staging/Production 环境失败关闭；
- `TECHNICIAN_IOS` 固定客户端元数据；
- OIDC Authorization Code + PKCE 请求、回调 state 校验、Token exchange/refresh/logout；
- `ThisDeviceOnly` Keychain Token Vault；
- HTTPS URLSession transport、Problem Details 安全文案与 trace/correlation 诊断；
- `/me` TECHNICIAN Context/Capability/导航加载及伪造 Context 拒绝；
- 同源生成 `ServiceOSCoreClient` 与 `ServiceOSDesignTokens` 的真实编译链接；
- Token、联系人、地址、VIN、照片路径和表单值的日志脱敏。

```bash
bash scripts/agent-verify.sh technician-ios
```

本机当前只有 Apple Command Line Tools，因此门禁只证明 Swift 6 严格编译与仓库内安全边界。完整 SwiftUI App、
Xcode project、Simulator、开发真机、签名和 TestFlight 必须在安装完整 Xcode 后由后续交付批次验证；本目录不把
Command Line Tools 结果冒充为 iOS App 可运行证据。
