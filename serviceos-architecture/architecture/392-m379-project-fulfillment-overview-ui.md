---
title: M379 项目履约只读总览与 Admin 入口
status: Implemented
milestone: M379
lastUpdated: 2026-07-20
relatedMilestones: [M378, M375]
openapiVersion: "1.0.60"
flywayVersion: "137"
---

# M379 项目履约只读总览与 Admin 入口

## 已实现

1. Admin 路由：`/projects/:id/fulfillment-profiles`、详情、预览；
2. 项目详情移除履约/SLA 空壳，改为「工单类型与履约配置」入口与摘要计数；
3. 列表/详情/运行说明书页面消费真实 API（`fulfillmentProfiles.ts` 薄封装）；
4. 运行预览表格数据来自服务端 `compile-preview`。

## 明确未实现

- 阶段编辑工作区（M380）；
- 四步发布流（M381）；
- 生成 `@serviceos/core-client` 正式依赖替换薄封装（M383）；
- Playwright E2E 全链路（随 M383）。

## 验证

```bash
cd serviceos-admin-web && npm run build
bash scripts/agent-verify.sh compile
```
