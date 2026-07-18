---
title: M264 Technician 在线资料上传交付批次
status: Implemented
milestone: M264
lastUpdated: 2026-07-18
relatedMilestones: [M11, M37, M38, M39, M52, M53, M119, M243, M257, M262, M263]
---

# M264 Technician 在线资料上传交付批次

## 1. 交付范围

M264 在当前责任任务详情上交付 Evidence 槽位查询、资料项安全摘要和在线三段式上传：

- Core OpenAPI 1.0.24 新增 Technician 专属 slot/item 查询及 Begin/Finalize；Begin 只接受文件元数据、
  `captureSource` 和 `capturedAt`，不接受 tenant、uploader、offline、locationVerified、onBehalf 或对象键；
- 后端先验证 ACTIVE TechnicianProfile/网点关系、`task.readAssigned`、资源网点和当前责任，再委托既有
  Evidence/File 内核复核 capability、RUNNING/guard、槽位数量、MIME、checksum、session owner 和幂等；
- H5 支持浏览器文件选择、SHA-256、受限 PUT、Finalize、进度和服务端状态；
- iOS 支持用户主动相机、PhotosPicker 和文件选择，资料只在当前前台操作内存中，不建立后台或离线队列；
- `uploadUrl`/`requiredHeaders` 只用于数据面 PUT；PUT 不携带 Bearer Token、Technician Context、客户端元数据
  或 Idempotency-Key，控制面 Begin/Finalize 仍实时鉴权。

本批不新增 Flyway。EvidenceItem/Revision、文件、审计、幂等结果和 Outbox 继续由既有事务边界原子提交。

## 2. 安全与状态语义

- 伪造/失效 Context 失败关闭；其他网点或非当前责任任务按不存在处理；
- 服务端构造 `offlineFlag=false` 和权威 uploader，不信任客户端可信元数据；
- 客户端只展示安全 item/revision 摘要，不返回 fileObjectId、uploader、原始 CaptureMetadata 或永久下载 URL；
- Finalize 返回 `STORED`/`VALIDATING` 等状态只表示已收件，不代表扫描、机器校验或审核完成；
- iOS 增加相机用途说明和 Photos/Videos 隐私清单声明，数据与用户关联、只用于 App Functionality、不追踪。

## 3. 工程证据

- `DefaultTechnicianEvidenceServiceTest`、`TechnicianEvidenceControllerSecurityTest`；
- `EvidenceItemRevisionPostgresIT` 证明 Begin/PUT/Finalize、checksum、数量和事务内核；
- Core OpenAPI 校验/兼容及 TypeScript、Swift 生成客户端消费者门禁；
- Technician H5 build 与 12 条 Playwright（含无凭证 PUT 和可信字段负向断言）；
- iOS Foundation smoke、Simulator/device/Production build、XCTest/XCUITest、Production arm64 archive 与隐私门禁；
- `ArchitectureTest` 与本里程碑唯一一次最终 L3 `bash scripts/verify-local.sh`。

## 4. 明确未实现

MCP-ONLINE-06/07 尚未完整验收：物理设备相机/相册、弱网失败重试、可恢复断点续传、后台上传、离线队列、
专业扫描服务、真实对象存储和生产设备大文件压力仍待后续证据。当前自动化证明在线前台单文件链路，不外推为
移动端可靠上传系统。

本批不创建 EvidenceSetSnapshot，不生成 Task 完成或 Visit 签退的真实 `operationRefs`，也未交付资料整改写闭环。
因此客户端仍不开放无引用签退。真实 IdP、签名真机、VoiceOver 人工走查和 TestFlight 也不在本批证据内。
