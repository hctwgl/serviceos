---
title: M254 Track A 多端共享基础总门禁验收矩阵
status: Implemented
milestone: M254
lastUpdated: 2026-07-18
---

# M254 Track A 多端共享基础总门禁验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M254-01 | TS Client | 确定性生成、CJS/ESM 编译、打包安装与独立消费 | `typescript-client.log` |
| M254-02 | Swift Client | 确定性生成、完整 Swift 6 编译与独立链接消费 | `swift-client.log` |
| M254-03 | 跨端机器源 | Token 与 Identity 两端生成稳定且负向探针通过 | design/identity logs |
| M254-04 | Core 包 | Web/iOS Core 独立严格编译和运行 | web/ios logs |
| M254-05 | 客户端元数据 | 服务端低基数规范化与 OpenAPI 约定通过 | metadata logs |
| M254-06 | 契约兼容 | merge-base 正向兼容及破坏/原地事件负向探针通过 | compatibility log |
| M254-07 | 证据清单 | 9 项全通过并生成 HEAD/OpenAPI/通过项 manifest | `target/client-foundation-gate/manifest.json` |

## 明确未验收

远端 CI、制品仓库、签名/SBOM、独立 Network/H5/iOS App 实际导入、真机和生产发布。
