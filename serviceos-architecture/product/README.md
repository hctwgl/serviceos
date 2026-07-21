# 产品设计

本目录保存建立在架构基线上的应用 PRD、用户旅程、页面清单、交互规则和验收标准。首批应用包括总部运营后台、网点工作台和师傅移动端。

开始任何跨 Portal、产品页面或前端产品化任务前，先阅读：[ServiceOS 产品、架构与交付决策基线](00-serviceos-product-delivery-decision-baseline.md)。该文件汇总已经确认的产品边界、Portal 策略、身份授权、履约执行、配置、技术原则、完成状态和待确认事项。

- [ServiceOS 产品、架构与交付决策基线](00-serviceos-product-delivery-decision-baseline.md)（跨产品、架构与交付的已确认决策入口）
- [跨 Portal 信息架构与应用外壳](01-cross-portal-information-architecture.md)
- [总部运营后台产品规格](02-admin-operations-portal-spec.md)
- [ServiceOS Admin 产品蓝图 v1.0](admin/README.md)（方案 A｜经典专业风、页面母版与验收门禁）
- [网点协作 Portal 产品规格](03-network-portal-spec.md)（业务职责、安全范围和领域行为）
- [Network Portal 产品设计基线](network/README.md)（方案 A｜经典专业协作风、核心页面母版与验收）
- [师傅移动端产品与离线交互规格](04-technician-mobile-app-spec.md)（业务、离线、上传、冲突和安全行为）
- [Technician 产品设计基线](technician/README.md)（方案 A｜经典专业移动作业风、H5/iOS 页面母版与验收）
- [跨 Portal 协作、命令反馈与状态交互](05-cross-portal-interaction-state-spec.md)
- [设计系统与可访问性规格](06-design-system-accessibility-spec.md)（status: Proposed）
- [06 → Admin 实施映射（M370）](06-admin-implementation-mapping-m370.md)（不改变 06 规范状态）
- [页面、动作、能力与数据范围矩阵](07-page-action-permission-matrix.md)

## 已批准视觉方向

| Portal | 已批准方案 | 事实源 |
|---|---|---|
| Admin | 方案 A｜经典专业风 | `admin/12-classic-professional-visual-baseline.md` |
| Network | 方案 A｜经典专业协作风 | `network/01-classic-professional-collaboration-baseline.md` |
| Technician H5/iOS | 方案 A｜经典专业移动作业风 | `technician/01-classic-professional-mobile-baseline.md` |

三个 Portal 共享 ServiceOS 品牌 Token、状态语义、中文业务词汇和服务端权限动作模型，但不得复用同一页面布局或通过前端角色判断动态变形为一个应用。

## 服务覆盖专项决策

- [高德省市行政区域服务覆盖决策与批准视觉基线](admin/13-service-coverage-amap-visual-baseline.md)：项目默认全国、网点按高德省市 `adcode` 配置，并以四张仓库视觉图作为强制实施参考。
