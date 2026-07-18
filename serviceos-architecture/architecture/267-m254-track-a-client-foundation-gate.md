---
title: M254 Track A 多端共享基础总门禁
status: Implemented
milestone: M254
lastUpdated: 2026-07-18
relatedMilestones: [M247, M248, M249, M250, M251, M252, M253]
---

# M254 Track A 多端共享基础总门禁

## 范围与证据

- 新增单一入口 `agent-verify.sh client-foundation`，顺序执行 9 个独立门禁；
- TypeScript/Swift OpenAPI Client 均两次干净生成并由仓库外临时消费者编译、安装或链接运行；
- Design Token 与 Client Identity 从各自单一 JSON 源确定性生成 Web/Swift 产物；
- Web/iOS Core 独立严格编译并运行请求、上下文、错误、Token 与客户端元数据 smoke；
- 服务端客户端元数据低基数规范化及 OpenAPI 声明分别独立测试；
- OpenAPI compatibility 以当前分支相对 `origin/master` merge-base 执行正负门禁；
- 每个分项输出独立日志，最终生成 `target/client-foundation-gate/manifest.json`，记录 HEAD、OpenAPI 版本和通过项；
- 首次真实运行结果为 9/9 PASS，Core OpenAPI 1.0.21；Flyway 仍为 100/102。

## Track A 完成边界

M247～M254 已完成仓库内共享工程底座和本地阻断门禁，可开始 Track B 独立 Network Web。这里不代表远端
制品仓库、签名/SBOM、正式应用导入、生产 OIDC、Keychain/Xcode 或支持能力协商已经完成。

## 明确未实现

远端 CI/制品留存（按项目决策当前关闭）、包发布、签名/SBOM、独立客户端应用、运行时兼容策略、灰度与生产发布。
