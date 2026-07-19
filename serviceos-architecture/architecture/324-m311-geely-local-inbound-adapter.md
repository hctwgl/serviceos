---
title: M311 吉利浩瀚本地入站适配器
status: Implemented
milestone: M311
lastUpdated: 2026-07-19
relatedMilestones: [M267, M272, M302, M304]
---

# M311 吉利浩瀚本地入站适配器

## 目标

基于《吉利接口文档 V1.3》落地第二家 OEM 本地可测切片：AES 解密契约、7.1 建单映射、通用 `InboundCreateWorkOrderPipeline`；真实 Sandbox/开放平台签名登记为 `BLOCKED_EXTERNAL`。

## 范围

- `GeelyAesCipher`（AES-128-CBC/PKCS5Padding；文档 4.5.2 示例密文可解密）
- `GeelyInboundCreateOrderService` + `/notify_create_order` HTTP
- 字段映射见 `integration/05-geely-haohan-v13-adapter-contract.md`
- ArchitectureTest 禁止核心域依赖 `integration.geely`
- 单元测试 + PostgreSQL IT（幂等重放）

## 明确未实现 / BLOCKED_EXTERNAL

- OpenAPI 平台统一签名 SDK
- Sandbox/生产 AK·SK·IV 联调
- 7.2～7.22 其余接口；材料/核销/取消全链路
- 不得声称真实吉利全链路 IMPLEMENTED

## 验证

```bash
bash scripts/agent-verify.sh test GeelyAesCipherTest,GeelyCreateOrderMapperTest
bash scripts/agent-verify.sh it GeelyInboundCreateOrderPostgresIT
```
