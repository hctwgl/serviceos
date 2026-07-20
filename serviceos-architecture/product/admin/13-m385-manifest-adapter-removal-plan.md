---
title: M385 Manifest 展示适配器删除计划
version: 0.1.0
status: Proposed
lastUpdated: 2026-07-20
---

# M385 Manifest 展示适配器删除计划

M383 的 `compile-preview` 当前只返回 `manifestJson + contentDigest`。旧前端在多个页面分别解析并直接暴露 JSON、Digest 和内部编码。

M385 第一批变更先移除普通页面中的 JSON/Digest，并把现有只读转换集中到：

```text
serviceos-admin-web/src/components/fulfillment/FulfillmentRunbookTable.vue
```

该组件只是当前契约下的临时展示适配器：

- 不参与运行时决策；
- 不计算 allowed-actions；
- 不替代服务端 Manifest Compiler；
- 不作为发布校验事实源；
- 不允许新增其他页面直接解析 `manifestJson`；
- 不构成 M385 产品验收完成。

## 删除门禁

该适配器必须在 M385 内删除，不得顺延为长期兼容层。删除前必须完成：

1. 服务端 `compile-preview` 返回产品化 Preview DTO；
2. DTO 包含阶段名称、责任方、表单名称、资料名称、动作名称、下一阶段、异常路径、SLA 和客户端支持；
3. 预览页和发布页只渲染产品化 DTO；
4. 正式页面不再执行 `JSON.parse(manifestJson)`；
5. 运行说明书和发布流程通过产品负责人截图验收。

前置条件未满足时，M385 必须保持 `PRODUCT_REJECTED` 或 `READY_FOR_REVIEW`，不得标记 `PRODUCT_ACCEPTED`。
