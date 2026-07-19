---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148～#162：M321～M335 Draft stacked
- 本切片分支：`cursor/m336-create-work-order-route-hint-88d5` — **M336** RouteHint（Draft，base=#162）
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`
- latestMilestone：**M336**
- Flyway：**125**；OpenAPI：**1.0.43**

## 本回合完成

### M336 CREATE_WORK_ORDER RouteHint

- 新增 `CreateWorkOrderRouteHint`；Pipeline/Materializer 改接路由提示
- 删除 `BydCpimMappedOrder`；BYD/GEELY/REF 建单适配器不再填领域字段
- 文档：`349-m336-*` / `333-m336-*`

### 既有 Draft 栈

- M321～M335（PR #148～#162）

## 验证

```text
bash scripts/agent-verify.sh test CreateWorkOrderMappingMaterializerTest,GeelyCreateOrderMapperTest
bash scripts/agent-verify.sh it ReferenceOemInboundOrderPostgresIT,GeelyInboundCreateOrderPostgresIT,MultiOemParallelCreateSmokePostgresIT,DualOemInboundRegressionPostgresIT,BydCpimInboundOrderHttpPostgresIT,BydCpimUpdateOrderHttpPostgresIT,BydCpimCancelOrderHttpPostgresIT,GeelyInboundCancelUpdatePostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight

## 下一步

1. DISPATCH 地图 scope / 比例分配
2. Update/Cancel Mapping 强制
3. 低代码深化
4. 吉利材料齐备后联调
