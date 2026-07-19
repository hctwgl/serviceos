---
title: M347 Admin INTEGRATION Mapping DSL 可视编辑 验收矩阵
status: Implemented
milestone: M347
lastUpdated: 2026-07-19
---

# M347 Admin INTEGRATION Mapping DSL 可视编辑 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M347-01 | INBOUND 选择 messageType | JSON 写入 CREATE/UPDATE/CANCEL | `PolicyAssetEditor.vue` + `data-testid=integration-message-type` |
| M347-02 | 切到 OUTBOUND | 清除 messageType | `setDirection` |
| M347-03 | constantValue / defaultValue / enumMap | 双向同步；constantValue 清空互斥字段 | `setMappingOptionalScalar` / `setMappingEnumMap` |
| M347-04 | 清空可选标量/枚举 | 从 JSON 删除键（非整项 merge 残留） | `replaceFieldMapping` |
| M347-05 | condition 增删与 operator 切换 | PRESENT 无 value；EQUALS 有 value；IN 有 values | `updateMappingCondition` |
| M347-06 | Admin build | 通过 | `npm run build` |

## 明确不验收

- OpenAPI/Flyway、吉利联调、DISPATCH 残留编辑器、Technician 执行器
