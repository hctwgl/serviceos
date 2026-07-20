---
title: M384 Admin 产品蓝图接受与 M383 产品状态纠正
status: Implemented
milestone: M384
lastUpdated: 2026-07-20
relatedMilestones: [M370, M377, M378, M383]
---

# M384 Admin 产品蓝图接受与 M383 产品状态纠正

## 目标

将已由产品负责人确认的 Admin 产品定位、核心角色、九个一级菜单和六张母版作为后续产品化事实源，并纠正“技术接入完成等于产品完成”的状态偏差。

## 已冻结

- 产品定位：ServiceOS 平台内部统一运营管理后台；
- 核心角色：平台超级管理员、运营管理员、工单运营、调度人员、审核人员、项目配置管理员、权限管理员、审计人员；
- 一级菜单：工作台、工单运营、服务履约、审核与整改、客户与项目、组织与资源、配置中心、系统管理、审计与监控；
- 六张母版：运营工作台、用户管理、项目管理、工单中心、工单详情、项目履约配置；
- Ant Design Vue 只承担组件实现，不替代信息架构和业务任务设计。

## M383 状态纠正

M378～M382 的领域与运行主链路保留。M383 面向产品负责人的状态调整为：

| 维度 | 状态 | 说明 |
|---|---|---|
| 技术 | `RUNTIME_CONNECTED` | Profile、Revision、Manifest、Resolver、发布、建单冻结和快照均已接入真实运行时 |
| 前端 | `FRONTEND_CONNECTED` | 列表、详情、编辑、预览、发布和快照已调用真实接口 |
| 产品 | `PRODUCT_REJECTED` | 新建硬编码、JSON 暴露、前端解析 Manifest、假影响分析和技术化编辑器未通过产品审查 |
| 质量 | 部分通过 | 功能与 a11y 有部分证据；完整视觉、人工产品审查和长链路未闭合 |

不得将 M383 标记为 `PRODUCT_ACCEPTED`。

## 验收事实源

- `product/admin/11-m383-product-review.md`：代码级产品审查；
- `product/admin/12-m384-fulfillment-route-acceptance-matrix.md`：逐路由状态和 M385 门禁；
- `product/admin/08-product-acceptance.md`：完成状态定义。

## 边界

M384 不修改后端运行语义、OpenAPI、Flyway、Capability、Scope 或 allowed-actions 计算。实际产品修正进入 M385。
