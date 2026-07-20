---
title: M385 documentJson 编辑适配器删除计划
version: 0.1.0
status: Proposed
lastUpdated: 2026-07-20
---

# M385 documentJson 编辑适配器删除计划

当前履约草稿接口仍以 `documentJson` 作为编辑契约。M385 第一批前端重构已将原始资产键输入改为业务选择器，并集中在 `FulfillmentProfileEditorPage.vue` 内完成一次结构转换，但这不是最终契约。

## 当前临时边界

- 普通用户不查看或编辑 JSON；
- 只有编辑页内部执行草稿文档序列化；
- 不新增第二套流程或状态机；
- 最终业务合法性仍由服务端校验和 allowed-actions 决定；
- 页面不能根据 JSON 自行放宽发布、建单或动作条件；
- 不允许将当前状态标记为 `PRODUCT_ACCEPTED`。

## 删除门禁

该适配器必须在 M385 内删除，不得成为长期双轨模型。前置条件：

1. 服务端提供结构化 Draft DTO；
2. 阶段、表单、资料、动作、责任、审核、SLA 和异常路径有明确字段契约；
3. 写接口支持结构化保存并保留乐观锁、幂等和审计语义；
4. 前端不再读取或提交 `documentJson`；
5. OpenAPI 和生成 TypeScript Client 同步；
6. 结构化编辑、校验、冲突恢复和发布 E2E 通过。

完成上述条件后，应删除编辑页中的 `parseStages`、`buildDocumentJson` 及其兼容注释。
