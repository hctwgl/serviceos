---
title: M333 入站 Mapping 无适配器 fallback 验收矩阵
status: Implemented
milestone: M333
lastUpdated: 2026-07-19
---

# M333 入站 Mapping 无适配器 fallback 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M333-01 | Mapping 仅提供必填键 + mobile | 物化成功；未映射可选字段为 null；Canonical 不含适配器姓名/地址 | `CreateWorkOrderMappingMaterializerTest` |
| M333-02 | Mapping 缺必填 `districtCode` | 失败关闭，即使适配器有值 | 同上 |
| M333-03 | Mapping 必填值为空白 | 失败关闭 | 同上 |
| M333-04 | BYD HTTP 全量 Mapping + UPPER | 工单写入 Mapping 权威值；Canonical 含 contentDigest | `BydCpimInboundOrderHttpPostgresIT` |
| M333-05 | 三 OEM 并行建单（BYD 含全量 INBOUND Mapping） | 三单成功；BYD 客户字段来自 Mapping | `MultiOemParallelCreateSmokePostgresIT` |
| M333-06 | 模块边界 | ArchitectureTest | ArchitectureTest |

## 明确不验收

- defaults/enum/condition DSL、强制零 Mapping 删除、OEM Mapper 拆除、吉利联调、OpenAPI/Flyway
