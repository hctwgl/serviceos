---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-18
---

# ServiceOS 自主 Agent 交接

## 当前分支和 HEAD

- 分支：`cursor/bc-71d073b6-2d93-418e-a750-0b73ee12db1c-0a6a`
- HEAD：与 `origin/master` 同步起点 `c70d7da8`（M266 + docs PR #145）
- 主线目标：阶段一多车企平台内核（用户连续推进提示词批准；Track F 延后）

## 当前目标

实施 **M267：多车企平台化验收标准 + 通用 Connector SPI + BYD CREATE_WORK_ORDER 入站边界归位**。

## 已完成内容

- 现场恢复：无未提交脏改、无开放 PR；latestMilestone=M266
- 本文件已创建

## 未完成内容

- M267 文档 / SPI / BYD 重构 / 防污染测试 / status 同步 / Draft PR
- M268+ 配置治理、EXCLUSIVE_GATEWAY、WAIT_EVENT、REFERENCE_OEM、双车企回归、第三家手册
- Track F 离线（后续）；Apple 签名/真机 = `BLOCKED_EXTERNAL`

## 关键设计决定

1. 用户提示词阶段一优先于 roadmap/05 Track F。
2. Connector 只存在于 `integration` 适配边界；核心域禁止车企协议分叉。
3. M267 最小闭环：CREATE_WORK_ORDER 入站 SPI + 通用管道；BYD 回调/出站归后续切片。
4. 阶段三解释为领域配置资产设计器，非通用页面低代码平台。

## 已执行验证

- 尚未开始代码验证（文档/代码实施中）

## 失败与阻塞

- 无工程阻塞
- 吉利/广汽 PDF 文本抽取工具受限 → REFERENCE_OEM 阶段标记 `TBD_EXTERNAL_CONTRACT`

## 下一步具体入口

1. `serviceos-architecture/roadmap/06-multi-oem-platform-kernel-delivery-plan.md`
2. `serviceos-architecture/decisions/ADR-085-generic-connector-spi.md`
3. `serviceos-architecture/architecture/280-m267-generic-connector-spi.md`
4. `com.serviceos.integration.spi` + `InboundCreateWorkOrderPipeline`
5. 重构 `BydCpimInboundOrderService` 委托管道
6. `ArchitectureTest` 防污染
7. `bash scripts/agent-verify.sh it BydCpimInboundOrderHttpPostgresIT` 与 `arch`
