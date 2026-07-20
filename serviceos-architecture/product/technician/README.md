---
title: ServiceOS Technician 产品设计基线
version: 1.0.0
status: Accepted
lastUpdated: 2026-07-20
owner: Product Owner
---

# ServiceOS Technician 产品设计基线

本目录把已批准的 **方案 A｜经典专业移动作业风** 固化为师傅端 H5/iOS 的正式产品设计事实源。

师傅端不是 Admin 工单详情的移动适配，也不是把桌面表格压缩到手机屏幕。它是面向现场执行的移动作业产品，必须在弱网、断网、频繁拍照、大文件上传和现场时间压力下，可靠完成任务。

## 已批准方向

- 蓝白企业移动产品；
- 单列卡片和步骤式执行；
- 当前任务、SLA 和下一步始终突出；
- 大触控区域和底部固定主操作；
- 拍摄、保存、上传、校验和提交状态明确分离；
- 本地已保存、待同步、服务器已确认不能混淆；
- 离线可继续采集，恢复联网后由服务端重新鉴权和校验；
- 不展示桌面表格、内部 ID、技术枚举和无关治理信息。

## 文档目录

1. [经典专业移动作业风视觉与交互基线](01-classic-professional-mobile-baseline.md)
2. [核心页面母版与产品验收](02-master-pages-and-acceptance.md)

## 上位事实源

实施前必须同时读取：

- `serviceos-architecture/product/00-serviceos-product-delivery-decision-baseline.md`
- `serviceos-architecture/product/04-technician-mobile-app-spec.md`
- `serviceos-architecture/product/01-cross-portal-information-architecture.md`
- `serviceos-architecture/product/05-cross-portal-interaction-state-spec.md`
- `serviceos-architecture/product/06-design-system-accessibility-spec.md`
- `serviceos-architecture/product/07-page-action-permission-matrix.md`

## H5 与 iOS 边界

- H5 与 iOS 共享产品语言、Page ID、动作语义、Schema 和在线参考流程；
- iOS 是正式现场生产客户端，承担 Keychain、本地加密数据库、原生相机/GPS、后台上传、OfflineCommand 和设备撤销；
- H5 是在线参考实现、契约联调、产品走查和受控轻量应急入口；
- H5 不承诺与 iOS 相同的后台可靠上传、长期离线恢复和设备级安全能力；
- 不得因页面视觉一致就把 H5 包装进 WebView 代替正式 iOS 能力。

## 使用规则

- 本目录决定师傅端的视觉语言、页面结构、母版和产品验收；
- `04-technician-mobile-app-spec.md` 继续决定离线、上传、安全、冲突和业务行为；
- OpenAPI、工作包、allowed-actions、OfflineCommand 和服务端版本校验继续是运行事实；
- 概念效果图只确定方向和结构，不替代真机、弱网和离线验收；
- 页面相似不能自动获得 `PRODUCT_ACCEPTED`、`VISUAL_APPROVED` 或生产就绪状态。
