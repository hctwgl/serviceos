---
title: 第三家车企接入手册
status: Accepted
lastUpdated: 2026-07-18
---

# 第三家车企接入手册

本手册给出在 ServiceOS 上接入第三家车企（OEM3）的标准路径。目标是：

```text
新增车企 ≈ Connector Adapter + Integration Mapping + Project/ServiceProduct + ConfigurationBundle + 版本化配置资产
```

禁止修改 WorkOrder/Workflow 核心逻辑，禁止 `if ("OEM3".equals(clientCode))` 分叉。

## 1. 前置输入（缺一则标记 BLOCKED_EXTERNAL）

- 接口协议与验签规则
- 错误码与状态映射
- 脱敏样例报文
- Sandbox / 凭据
- 资料/审核要求差异说明

仓库内吉利/广汽 PDF 可作为调研输入，未确认项保持 `TBD_EXTERNAL_CONTRACT`。

## 2. 工程步骤

1. **创建适配器包** `integration.<oem3>`（仅协议层）  
   - SignatureVerifier / ReplayGuard / InboundMapper / OutboundMapper  
   - 实现后委托 `InboundCreateWorkOrderPipeline`（见 ADR-085 / M267）
2. **不要写领域表**；只产生 `InboundEnvelope` → `CanonicalMessage` → `ReceiveExternalWorkOrderCommand`
3. **发布配置资产**  
   - 优先复用 `configuration/templates/home-charging-survey-install/`  
   - 差异用 INTEGRATION / FORM / EVIDENCE / RULE 新版本表达
4. **创建 Project + Bundle**  
   - 独立 `clientCode` / brand / 区域适用性  
   - 零命中/多命中必须失败关闭
5. **契约**  
   - 独立 OpenAPI（或版本化路径）；破坏性变更需获批
6. **安全**  
   - 适配器认证与 OIDC 分离；Secret 不入库
7. **测试最低集**  
   - 验签失败 / 重放 / 同键冲突 / 配置零多命中  
   - 与 BYD、REFERENCE_OEM 并行冒烟（见 M273）  
   - ArchitectureTest：核心域不得依赖 `integration.<oem3>`
8. **文档**  
   - 适配器契约、映射表、验收矩阵、更新 `roadmap/06` 与 implementation-status

## 3. 完成定义

- OEM3 独立 Connector + 独立 Bundle  
- 安装主链路可跑通（或 SAMPLE + TBD 清单）  
- 核心域无车企协议判断  
- 第三家接入不再需要复制 WorkOrder/Workflow 代码

## 4. 参考实现

- BYD：生产向协议切片（M56～M60）  
- REFERENCE_OEM：SAMPLE SPI 演示（M272）  
- 标准家充模板：M271
