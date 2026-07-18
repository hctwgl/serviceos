---
title: M262 Technician 在线 Visit 交付批次
status: Implemented
milestone: M262
lastUpdated: 2026-07-18
relatedMilestones: [M243, M245, M257, M258, M259, M260, M261]
---

# M262 Technician 在线 Visit 交付批次

## 1. 交付范围

M262 是多端 Track E 的首个可实施切片，在既有当前责任任务详情上交付在线签到和无法施工中断：

- Core OpenAPI 1.0.22 新增 Technician 专用 check-in、check-out、interrupt 命令路径；请求不能携带
  tenant、network、technician、receivedAt，签到固定为 `offline=false`；
- 后端先验证 `X-Technician-Context` 对应当前主体的 ACTIVE TechnicianProfile 与网点关系，再校验资源网点，
  最终委托既有 Visit 聚合重新验证当前责任、Capability、幂等、版本、审计与 Outbox；
- 独立 Technician H5 在用户点击时一次性调用浏览器定位，支持签到和已接受原因码的无法施工中断，并明确
  浏览器定位不等于原生设备可信度；
- 原生 SwiftUI App 接入当前任务 Feed/详情、一次性 CoreLocation、签到和中断；隐私清单同步声明精确位置与
  Device ID 用于 App Functionality、关联用户但不用于 tracking；
- H5/iOS 都不生成占位 `operationRefs`。服务端 check-out 契约已准备好，但客户端必须等真实现场操作引用存在
  后才开放签退动作。

本批不新增 Flyway；现有 Visit 聚合继续保证聚合、审计、幂等结果与 Outbox 同事务提交。

## 2. 安全与失败关闭

- 伪造或失效网点 Context 返回 `PORTAL_CONTEXT_INVALID`；资源属于其他网点时按不存在处理，避免 ID 探测；
- VisitService 在适配层之后再次按当前责任和 Capability 校验，因此改派、关系终止或撤权后不能继续写；
- check-in 的 `Idempotency-Key` 必须等于 `deviceCommandId`，服务端写入权威 `receivedAt`；
- interrupt 要求带引号的正整数 `If-Match`，异常码只使用既有 `SITE_UNSAFE`、`MATERIAL_MISSING`；
- 定位仅由明确用户操作触发，不启用持续或后台定位，不声明离线能力。

## 3. 工程证据

- `TechnicianVisitControllerSecurityTest`：匿名、缺失 Context、强制在线语义、严格 ETag；
- `TechnicianPortalFeedPostgresIT`：真实 PostgreSQL 上伪造网点拒绝、在线 Visit/Appointment 状态持久化；
- Core OpenAPI 校验、兼容门禁及 TypeScript/Swift 生成客户端消费者门禁；
- Technician H5 build 与 Playwright 在线签到/中断；
- iOS Foundation smoke、Simulator App/XCTest/XCUITest 与 Production arm64 distribution archive 门禁；
- `ArchitectureTest` 与本里程碑最终 L3 `bash scripts/verify-local.sh`。

## 4. 明确未实现

ADR-082 尚未接受 Technician 联系写所需的权威联系人引用和联系策略，因此没有越过该边界实现联系/预约写。
动态表单、Evidence、现场操作提交和整改在线闭环仍待后续 Track E 批次；客户端签退也因此暂不开放。

真实设备的位置权限/精度、真实 IdP、签名安装、VoiceOver 人工听读和 TestFlight 尚未验收；Simulator 与无签名
archive 不能替代这些证据。离线工作包、命令队列、冲突与恢复属于 Track F，M262 不作任何离线声明。
