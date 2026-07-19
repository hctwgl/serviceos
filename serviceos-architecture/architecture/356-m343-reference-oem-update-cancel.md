---
title: M343 REFERENCE_OEM SAMPLE Update/Cancel Mapping
status: Implemented
milestone: M343
lastUpdated: 2026-07-19
relatedMilestones: [M335, M336, M339, M272]
---

# M343 REFERENCE_OEM SAMPLE Update/Cancel Mapping

## 目标

为 REFERENCE / SAMPLE 第二车企演示适配器补齐 UPDATE/CANCEL 入站：HMAC 验签 →
RouteHint → 强制冻结 INBOUND Mapping 物化，与 BYD/GEELY M339 同构。

## 范围与非目标

- 范围：
  - `update-orders` / `cancel-orders` 端点与 Security permitAll
  - Update/Cancel Service：仅 RouteHint；领域字段由 Mapping 物化
  - Bundle CREATE+UPDATE+CANCEL 夹具；create→update→cancel + 重放 IT
- 明确不做：
  - OpenAPI / Flyway（SAMPLE / TBD_EXTERNAL_CONTRACT）
  - 真实第二车企协议或吉利 Sandbox
  - Dual/Multi-OEM Update 冒烟扩展

## 已实现

- SAMPLE Update/Cancel HTTP + Mapping 强制；IT 证明地址更新与 CANCELLED

## 明确未实现

- 真实 OEM 合同、Admin Mapping UI、吉利联调

## 验证命令

```bash
bash scripts/agent-verify.sh test UpdateWorkOrderMappingMaterializerTest,CancelWorkOrderMappingMaterializerTest,ArchitectureTest
bash scripts/agent-verify.sh it ReferenceOemInboundUpdateCancelPostgresIT,ReferenceOemInboundOrderPostgresIT
```
