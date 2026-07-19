---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR #148～#161：M321～M334 Draft stacked
- 本切片分支：`cursor/m335-inbound-create-mapping-required-88d5` — **M335** CREATE 强制 Mapping（Draft，base=#161）
- `master`：`32b902f897d19d2c906acac899990bf1aa2bb056`
- latestMilestone：**M335**
- Flyway：**125**；OpenAPI：**1.0.43**

## 本回合完成

### M335 CREATE_WORK_ORDER 强制 INBOUND Mapping

- 管道移除零 Mapping 回退；缺失 Mapping → `INTEGRATION_MAPPING_FAILED`
- BYD/REF/GEELY 建单与冒烟/回归夹具补齐 INBOUND Mapping
- 负例：workflow-only Bundle 拒绝建单
- 文档：`348-m335-*` / `332-m335-*`

### 既有 Draft 栈

- M321～M334（PR #148～#161）

## 验证

```text
bash scripts/agent-verify.sh it ReferenceOemInboundOrderPostgresIT,GeelyInboundCreateOrderPostgresIT,MultiOemParallelCreateSmokePostgresIT,DualOemInboundRegressionPostgresIT,BydCpimInboundOrderHttpPostgresIT,BydCpimUpdateOrderHttpPostgresIT,BydCpimCancelOrderHttpPostgresIT,GeelyInboundCancelUpdatePostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```

## BLOCKED_EXTERNAL

- 吉利 Sandbox/OpenAPI 签名/真实脱敏报文
- Swift/Xcode、签名真机、TestFlight

## 下一步

1. 删除 OEM 入站 Java 字段 Mapper / RouteHint DTO
2. DISPATCH 地图/比例分配；低代码深化
3. 吉利材料齐备后联调
