---
title: ServiceOS Network Portal 产品设计基线
version: 1.0.0
status: Accepted
lastUpdated: 2026-07-20
owner: Product Owner
---

# ServiceOS Network Portal 产品设计基线

本目录把已批准的 **方案 A｜经典专业协作风** 固化为网点端的正式产品设计事实源。

网点端不是总部 Admin 的裁剪皮肤，也不是师傅端的桌面放大版。它面向网点负责人及获授权协作人员，围绕本网点工单、师傅调度、预约协同、资料整改、资质和产能完成日常工作。

## 已批准方向

- 浅色企业协作平台；
- 白色或极浅导航；
- ServiceOS 企业蓝主色；
- 中高信息密度；
- 清晰边框、克制阴影；
- 当前责任、SLA、风险和下一步优先；
- 桌面端优先，同时兼顾平板和有限移动 Web；
- 不显示其他网点、总部内部价格和无关治理信息；
- 不暴露 UUID、Capability、Scope、技术枚举和原始接口结构。

## 文档目录

1. [经典专业协作风视觉与交互基线](01-classic-professional-collaboration-baseline.md)
2. [核心页面母版与产品验收](02-master-pages-and-acceptance.md)

## 上位事实源

实施前必须同时读取：

- `serviceos-architecture/product/00-serviceos-product-delivery-decision-baseline.md`
- `serviceos-architecture/product/03-network-portal-spec.md`
- `serviceos-architecture/product/01-cross-portal-information-architecture.md`
- `serviceos-architecture/product/05-cross-portal-interaction-state-spec.md`
- `serviceos-architecture/product/06-design-system-accessibility-spec.md`
- `serviceos-architecture/product/07-page-action-permission-matrix.md`

## 使用规则

- 本目录决定网点端的视觉语言、页面结构、母版和产品验收；
- `03-network-portal-spec.md` 继续决定业务职责、页面能力、安全范围和领域行为；
- OpenAPI、Capability、DataScope、allowed-actions 和服务端 Context 继续是运行事实；
- 概念效果图只确定方向和结构，不替代真实接口、真实数据和逐页验收；
- 当前页面不能因“看起来接近效果图”自动获得 `PRODUCT_ACCEPTED` 或 `VISUAL_APPROVED`。
