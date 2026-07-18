---
title: M263 Technician 在线基础表单交付批次
status: Implemented
milestone: M263
lastUpdated: 2026-07-18
relatedMilestones: [M33, M34, M35, M52, M53, M243, M246, M257, M258, M259, M262]
---

# M263 Technician 在线基础表单交付批次

## 1. 交付范围

M263 在当前责任任务详情上交付冻结表单的在线基础字段渲染与不可变提交：

- Core OpenAPI 1.0.23 新增 Technician 当前任务表单查询与提交路径；请求只接受 `formVersionId` 和
  `values`，不接受 tenant、actor、submittedBy 或客户端伪造的 prefillVersion；
- 后端验证 ACTIVE TechnicianProfile/网点关系、`task.readAssigned`、资源所属网点和当前责任，再委托既有
  Forms 服务重新验证 `form.read`/`form.submit`、冻结 FormVersion、Task RUNNING/guard、幂等、审计与 Outbox；
- H5 与原生 SwiftUI App 支持 STRING、TEXT、INTEGER、DECIMAL、BOOLEAN、DATE、DATETIME 基础字段，保留
  数字和布尔值的 JSON 类型，展示服务端字段级校验问题，并在成功后读取不可变提交结果；
- 输入只保存在当前页面内存，不声明草稿、杀进程恢复或离线能力。

本批不新增 Flyway。表单提交、审计、幂等结果与 Outbox 继续由既有 Forms 事务边界原子提交。

## 2. 安全与失败关闭

- 伪造/失效 Context 返回 `PORTAL_CONTEXT_INVALID`；其他网点或非当前责任任务按不存在处理，避免 ID 探测；
- H5/iOS 发现条件 required/visible/editable/default、validationRules、optionsRef、validators 或未知字段类型时，
  禁止提交并提示当前客户端不支持，不能以忽略规则方式绕过服务端语义；
- 服务端仍是最终校验权威，返回 M53 已实现的稳定问题码和字段键；客户端不把失败伪装为成功；
- Idempotency-Key 由客户端每次显式提交生成；刷新或重复网络请求由服务端幂等边界收敛。

## 3. 工程证据

- `DefaultTechnicianFormServiceTest`、`TechnicianFormControllerSecurityTest` 与真实 PostgreSQL Portal IT；
- Core OpenAPI 校验/兼容及 TypeScript、Swift 生成客户端消费者门禁；
- Technician H5 build 与 Playwright 基础类型、请求边界、成功结果和无伪草稿断言；
- iOS Foundation smoke、Simulator/device build、XCTest/XCUITest；
- `ArchitectureTest` 与本里程碑唯一一次最终 L3 `bash scripts/verify-local.sh`。

## 4. 明确未实现

完整 MCP-ONLINE-05 尚未完成：可移植客户端条件表达式、validationRules、ENUM/optionsRef、附件/签名/定位等
高级控件、服务端预填版本、草稿所有权/版本冲突、更正与审核仍待接受的共享契约和后续批次。页面内存输入不是
LocalDraft，也不能证明断网、杀进程或重启恢复。

Evidence、真实 operationRefs 签退、整改在线闭环和 Track F 离线工作包仍未实现。真实 IdP、签名真机、
VoiceOver 人工走查和 TestFlight 也不在本批证据内。
