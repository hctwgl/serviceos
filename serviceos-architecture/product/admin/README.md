---
title: ServiceOS Admin 产品蓝图 v1.0
version: 0.1.0
status: Proposed
lastUpdated: 2026-07-20
---

# ServiceOS Admin 产品蓝图 v1.0

本目录定义 ServiceOS 总部运营管理后台的产品事实源。它不替代领域模型、OpenAPI、Capability、Scope、allowed-actions 或服务端 Navigation，而是规定这些技术能力应如何以运营人员可理解的方式组织成产品。

## 已由产品负责人锁定

- 产品定位：平台内部统一运营管理后台。
- 核心角色：平台超级管理员、运营管理员、工单运营、调度人员、审核人员、项目配置管理员、权限管理员、审计人员。
- 一级菜单：工作台、工单运营、服务履约、审核与整改、客户与项目、组织与资源、配置中心、系统管理、审计与监控。
- 普通运营界面不得成为 API、领域命令或配置 JSON 调试器。
- Ant Design Vue 是基础组件实现，不等同于产品信息架构；页面必须先满足业务任务，再映射技术能力。

## 文档目录

1. [产品定位与原则](01-product-positioning.md)
2. [角色与任务矩阵](02-role-task-matrix.md)
3. [信息架构与菜单](03-information-architecture.md)
4. [目标页面清单](04-page-inventory.md)
5. [页面模式与交互规范](05-page-patterns.md)
6. [六张母版页面](06-master-pages.md)
7. [技术能力到产品能力的映射](07-technical-to-product-mapping.md)
8. [产品验收与里程碑状态](08-product-acceptance.md)
9. [实施路线图](09-roadmap.md)
10. [当前路由到目标产品映射](10-current-to-target-matrix.md)

## 规范优先级

1. 已接受的领域 ADR、数据库约束、OpenAPI 和安全边界；
2. `product/01-cross-portal-information-architecture.md`；
3. `product/06-design-system-accessibility-spec.md`；
4. 本 Admin 产品蓝图；
5. 页面级实现说明与 Agent 提示词。

出现冲突时，技术安全和领域事实不降低；产品层必须通过业务文案、渐进披露和专用工作区消化技术复杂度，不能把复杂度原样暴露给普通用户。

## 使用规则

任何 Admin 功能只有同时达到以下状态，才允许在面向产品负责人的实施状态中写“已完成”：

- 真实后端能力可用；
- 前端已接入真实接口；
- 页面符合本蓝图；
- 产品负责人完成页面审查；
- 功能、视觉与可访问性验收通过。

只有路由、PageContainer、接口调用或自动化测试通过，不构成产品完成。