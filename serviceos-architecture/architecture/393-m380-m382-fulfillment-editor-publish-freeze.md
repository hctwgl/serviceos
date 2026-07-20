---
title: M380–M382 履约编辑、发布与建单冻结
status: Implemented
milestone: M382
lastUpdated: 2026-07-20
relatedMilestones: [M378, M379]
openapiVersion: "1.0.60"
flywayVersion: "137"
---

# M380–M382 履约编辑、发布与建单冻结

## 已实现

### M380
- Admin `FulfillmentProfileEditorPage` 三栏阶段编排工作区；
- 草稿保存、校验跳转、阶段增删排序；
- 路由 `.../edit`。

### M381
- DedicatedFlow 四步发布页（校验→预览→影响→生效发布）；
- 明确存量工单不受影响文案。

### M382
- `InboundCreateWorkOrderPipeline` 接入 `ProjectFulfillmentResolver`；
- 有 Profile 时失败关闭并冻结 `PROFILE_REVISION`；
- 无 Profile 时 Bundle 解析 + `LEGACY_BUNDLE`（迁移路径，非草稿回退）；
- `ReceiveExternalWorkOrderCommand` 扩展冻结字段；
- 工单 `GET .../fulfillment-snapshot`。

## 明确未实现 / DOMAIN_GAP

- 完整 30 条校验规则与动作目录产品化选择器；
- allowed-actions 阻塞原因结构化扩展；
- Playwright 全链路 E2E 与 a11y/视觉收口（M383）；
- `@serviceos/core-client` 替换 Admin 薄封装（M383）；
- 无 Profile 项目强制失败（当前 LEGACY 过渡，需试点种子后收紧）。
