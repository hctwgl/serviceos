---
title: ADR-085：通用 Connector SPI 与 Canonical 入站管道
status: Accepted
date: 2026-07-18
---

# ADR-085：通用 Connector SPI 与 Canonical 入站管道

## 背景

BYD CPIM 已实现 Envelope / Canonical / OutboundDelivery 纵向切片，但适配逻辑与通用管道耦合在 `integration.byd` 服务内。第二家车企接入前必须抽出可复用边界，否则会出现核心域车企分叉或复制聚合。

## 决策

1. 在 `integration.spi` 发布连接器扩展契约：`ConnectorIdentity`、建单 Canonical 命令模型，以及适配器在完成协议验签/映射后提交给平台的端口。
2. 在 `integration.application` 提供 `InboundCreateWorkOrderPipeline`：负责 Bundle 解析、原文/Canonical 私有存储引用、CanonicalMessage 登记、调用 `WorkOrderCommandService.receive`、完成 Envelope/Canonical、写出 `integration.canonical-message-processed` 与审计入口参数。
3. 车企协议差异（验签、防重放 Nonce、DTO、HTTP 响应码）只留在 `integration.<oem>` 适配器；适配器不得直接写 `wo_` / `wfl_` / `tsk_` / `evd_` 等业务表。
4. 权威路径固定为：

```text
Connector Adapter
→ InboundEnvelope
→ CanonicalMessage
→ 领域命令
→ 领域事件 / Outbox
```

5. 核心模块（workorder/workflow/task/dispatch/sla/forms/evidence/fieldwork/appointment 等）不得依赖任何 `integration.<oem>` 包；允许持有通用 `clientCode` 业务属性，禁止协议级分支。
6. M267 仅强制 CREATE_WORK_ORDER 入站归位；BYD 审核回调与出站 Delivery 可在后续里程碑继续迁入同一 SPI 族，不得在本 ADR 留下空的 Outbound 接口冒充完成。

## 后果

- 第二家车企以新适配器 + Mapping + 独立 Bundle 接入，不修改 WorkOrder/Workflow 核心。
- 现有 BYD 外部 OpenAPI/验签行为保持；内部改为委托通用管道。
- 架构测试必须能阻断核心域对 OEM 适配包的依赖扩散。
