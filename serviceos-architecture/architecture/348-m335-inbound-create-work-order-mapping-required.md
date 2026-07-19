---
title: M335 CREATE_WORK_ORDER 强制 INBOUND Mapping
status: Implemented
milestone: M335
lastUpdated: 2026-07-19
relatedMilestones: [M321, M333, M334]
---

# M335 CREATE_WORK_ORDER 强制 INBOUND Mapping

## 目标

`InboundCreateWorkOrderPipeline` 对 CREATE_WORK_ORDER 强制要求冻结 Bundle 中
connector 唯一 INBOUND INTEGRATION Mapping；缺失则失败关闭，不再回退适配器领域字段。

## 范围与非目标

- 范围：
  - 管道移除零 Mapping 兼容路径；始终物化 + Mapping 审计
  - BYD / REFERENCE_OEM / GEELY 建单相关 IT 与冒烟夹具补齐 INBOUND Mapping
  - 负例：workflow-only Bundle 拒绝建单
- 明确不做：
  - 删除 OEM 协议适配器或 Java 字段 Mapper 类
  - Update/Cancel 管道 Mapping 强制
  - Bundle 路由提示（brand/product/province）去适配器化
  - OpenAPI / Flyway / 吉利 Sandbox

## 已实现

- CREATE_WORK_ORDER 强制 Mapping + 全 OEM 建单夹具 + 负例

## 明确未实现

- 删除 OEM Java Mapper / 引入纯 RouteHint DTO
- Update/Cancel Mapping 强制
- DISPATCH 地图/比例、吉利联调

## 验证命令

```bash
bash scripts/agent-verify.sh it ReferenceOemInboundOrderPostgresIT,GeelyInboundCreateOrderPostgresIT,MultiOemParallelCreateSmokePostgresIT,DualOemInboundRegressionPostgresIT,BydCpimInboundOrderHttpPostgresIT,BydCpimUpdateOrderHttpPostgresIT,BydCpimCancelOrderHttpPostgresIT,GeelyInboundCancelUpdatePostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```
