---
title: M303 INTEGRATION Mapping 运行时
status: Implemented
milestone: M303
lastUpdated: 2026-07-19
relatedMilestones: [M16, M295, M302]
---

# M303 INTEGRATION Mapping 运行时

## 目标

从工单冻结 Bundle 加载 INTEGRATION Mapping，按白名单 Transform 映射入站字段，失败关闭并可解释。

## 范围

- `IntegrationMappingRuntime.applyInbound`
- Transform：NONE / TRIM / UPPER / LOWER / DATE_ISO
- mappingKey 零/多命中、必填缺失失败关闭
- 单元测试 + 冻结 Bundle Postgres IT

## 明确未实现

- 出站 Mapping 应用；接入 BYD 建单主路径替换硬编码 Mapper；任意脚本

## 验证

```bash
bash scripts/agent-verify.sh test DefaultIntegrationMappingRuntimeTest
bash scripts/agent-verify.sh it IntegrationMappingRuntimePostgresIT
```
