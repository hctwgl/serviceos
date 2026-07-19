---
title: M336 CREATE_WORK_ORDER RouteHint 与 OEM 字段 Mapper 瘦身
status: Implemented
milestone: M336
lastUpdated: 2026-07-19
relatedMilestones: [M335, M333, M334]
---

# M336 CREATE_WORK_ORDER RouteHint 与 OEM 字段 Mapper 瘦身

## 目标

适配器不再构造完整 `CreateWorkOrderMappedInbound` 领域字段；仅提交
`CreateWorkOrderRouteHint`（Bundle 路由 + businessKey 基底），领域建单由冻结
INBOUND Mapping 物化。

## 范围与非目标

- 范围：
  - 新增 `CreateWorkOrderRouteHint`
  - Pipeline / Materializer 改接 RouteHint
  - BYD / GEELY / REFERENCE_OEM 建单适配器瘦身；删除 `BydCpimMappedOrder`
  - GeelyCreateOrderMapper → `toRouteHint`
- 明确不做：
  - Update/Cancel Mapping 强制或 RouteHint 化
  - Bundle 路由完全配置化（仍用适配器 brand/product/province 提示）
  - OpenAPI / Flyway / 吉利 Sandbox

## 已实现

- RouteHint SPI + 三 OEM 建单瘦身 + 单元/IT

## 明确未实现

- Update/Cancel Mapper 拆除、DISPATCH 地图/比例、低代码深化、吉利联调

## 验证命令

```bash
bash scripts/agent-verify.sh test CreateWorkOrderMappingMaterializerTest,GeelyCreateOrderMapperTest
bash scripts/agent-verify.sh it ReferenceOemInboundOrderPostgresIT,GeelyInboundCreateOrderPostgresIT,MultiOemParallelCreateSmokePostgresIT,DualOemInboundRegressionPostgresIT,BydCpimInboundOrderHttpPostgresIT,BydCpimUpdateOrderHttpPostgresIT,BydCpimCancelOrderHttpPostgresIT,GeelyInboundCancelUpdatePostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```
