---
title: M333 入站 Mapping 物化无适配器 fallback
status: Implemented
milestone: M333
lastUpdated: 2026-07-19
relatedMilestones: [M321, M304, M331, M332]
---

# M333 入站 Mapping 物化无适配器 fallback

## 目标

当冻结 Bundle 含 connector 唯一 INBOUND INTEGRATION Mapping 并进入物化时，建单领域字段
仅取自 Mapping `internalFields`，不再回退适配器薄兼容层取值。

## 范围与非目标

- 范围：
  - `CreateWorkOrderMappingMaterializer`：必填地理/产品键仅 Mapping；可选字段省略 → null
  - `InboundCreateWorkOrderPipeline`：Mapping 输入侧临时播种 `brandCode`/`serviceProductCode`
    （待 defaults DSL 退出）
  - `clientCode` 仍来自 connector 身份；`businessKey` 仅按 Mapping `externalOrderCode` 重写后缀
  - 零 Mapping 路径保持适配器兼容
  - 单元测试 + BYD HTTP IT + 三 OEM 并行冒烟夹具补齐全量 Mapping 字段
- 明确不做：
  - defaults / enum / condition / constant DSL
  - 全局强制要求 INBOUND Mapping（删除零 Mapping 路径）
  - 删除 OEM 入站 Java Mapper
  - Update/Cancel 管道 Mapping 物化
  - OpenAPI / Flyway 变更

## 已实现

- Mapping 命中后无适配器字段 fallback + IT/冒烟夹具

## 明确未实现

- defaults/enum/condition DSL（并移除 brand/product 播种）→ 见 **M334**
- 强制全部 connector 配置 INBOUND Mapping
- 删除 OEM 入站 Mapper、吉利联调、DISPATCH 地图/比例

## 验证命令

```bash
bash scripts/agent-verify.sh test CreateWorkOrderMappingMaterializerTest
bash scripts/agent-verify.sh it MultiOemParallelCreateSmokePostgresIT,BydCpimInboundOrderHttpPostgresIT
bash scripts/agent-verify.sh test ArchitectureTest
```
