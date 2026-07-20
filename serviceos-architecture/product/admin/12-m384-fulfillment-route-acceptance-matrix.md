---
title: M384 项目履约配置逐路由验收矩阵
version: 0.1.0
status: Proposed
lastUpdated: 2026-07-20
baseline: master after PR #205
---

# M384 项目履约配置逐路由验收矩阵

本矩阵将 M383 的“技术已接入”与“产品已验收”分开记录。只有单页同时达到 `FRONTEND_COMPLETE`、`PRODUCT_ACCEPTED`、`TEST_PASSED`、`VISUAL_APPROVED` 与 `A11Y_APPROVED`，才允许标记完成。

## 状态说明

- 技术：`RUNTIME_CONNECTED` 表示真实后端、数据库和运行链路已接入；
- 前端：`FRONTEND_CONNECTED` 表示已调用真实接口，但页面仍可能是技术界面；
- 产品：`READY_FOR_REVIEW` 只表示达到人工审查条件，不等于批准；
- 质量：未实际运行或未人工查看时必须写 `NOT_REVIEWED`。

## 路由矩阵

| 路由 | 页面 | 技术状态 | 前端状态 | 产品状态 | 质量状态 | 当前主要问题 | M385 目标 |
|---|---|---|---|---|---|---|---|
| `/projects/:id/fulfillment-profiles` | 工单类型与履约配置列表 | `RUNTIME_CONNECTED` | `FRONTEND_CONNECTED` | `PRODUCT_REJECTED` | 部分自动测试 | “新增工单类型”直接硬编码创建固定方案 | 主操作进入独立新建向导；列表只负责查询、比较和进入详情 |
| `/projects/:id/fulfillment-profiles/new` | 新建工单类型配置 | `API_AVAILABLE` | `FRONTEND_NOT_STARTED` | `PRODUCT_NOT_DESIGNED` | `TEST_NOT_RUN` | 当前无独立路由 | 选择工单类型、填写名称说明、选择标准/复制/空白起始方式并确认创建 |
| `/projects/:id/fulfillment-profiles/:profileId` | 配置详情 | `RUNTIME_CONNECTED` | `FRONTEND_CONNECTED` | `PRODUCT_REJECTED` | `NOT_REVIEWED` | 前端解析草稿 JSON；技术引用直接展示 | 使用服务端结构化只读 DTO；显示版本状态、当前生效标识和差异入口 |
| `/projects/:id/fulfillment-profiles/:profileId/edit` | 配置编辑工作区 | `RUNTIME_CONNECTED` | `FRONTEND_CONNECTED` | `PRODUCT_REJECTED` | a11y 部分通过 | 直接读写 `documentJson`；普通区显示技术编码和聚合版本；单文件过大 | 结构化 Draft DTO、实体选择器、业务组件拆分、技术信息进入诊断区 |
| `/projects/:id/fulfillment-profiles/:profileId/preview` | 工单运行说明书 | `RUNTIME_CONNECTED` | `FRONTEND_CONNECTED` | `PRODUCT_REJECTED` | `NOT_REVIEWED` | 前端自行解析 Manifest 并展示 ownerType、targetStage、slaRef | 服务端返回产品化 Preview DTO；前端仅渲染中文业务内容 |
| `/projects/:id/fulfillment-profiles/:profileId/publish` | 发布新版本 | `RUNTIME_CONNECTED` | `FRONTEND_CONNECTED` | `PRODUCT_REJECTED` | `NOT_REVIEWED` | 发布步骤暴露 Manifest JSON/Digest；影响分析为固定说明 | 四步发布保留，但第二步为运行说明书，第三步为真实 Revision Compare/Impact |
| `/work-orders/:id/configuration-snapshot` | 工单配置快照 | `RUNTIME_CONNECTED` | `FRONTEND_CONNECTED` | `PRODUCT_REJECTED` | `NOT_REVIEWED` | 仅显示表单/资料/动作数量，无法解释规则依据 | 展示冻结版本、阶段要求、资料原因、动作阻塞原因和配置依据 |

## 本轮硬门禁

1. 正式页面不得显示完整 Manifest JSON、Digest、API 地址、If-Match 或 Idempotency-Key；
2. 前端不得解析 Manifest 形成第二套业务解释；
3. 普通配置人员不得输入资产 UUID、stageCode、taskType code 或 slaRef；
4. 编辑、校验、预览、发布、暂停、恢复和停用必须由服务端 Profile allowed-actions 控制；
5. 发布影响分析必须来自服务端真实比较结果；
6. 所有截图必须在产品审查后才能建立视觉金标。

## M384 结论

M383 当前统一记录为：

- 技术：`RUNTIME_CONNECTED`；
- 前端：`FRONTEND_CONNECTED`；
- 产品：`PRODUCT_REJECTED`；
- 质量：功能与 a11y 部分通过，视觉未批准。

M385 完成并经产品负责人明确批准之前，不得写“项目工单履约配置产品化完成”。
