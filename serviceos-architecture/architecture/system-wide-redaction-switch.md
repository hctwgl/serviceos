---
title: ServiceOS 全局数据脱敏开关
version: 0.1.0
status: Implemented Foundation
---

# ServiceOS 全局数据脱敏开关

## 1. 决策

ServiceOS 使用一个总开关控制业务数据脱敏：

```text
SERVICEOS_REDACTION_ENABLED=false
```

也可通过 JVM 系统属性设置：

```text
-Dserviceos.redaction.enabled=true
```

默认值为 `false`。

| 开关 | 业务数据 | 安全凭据 |
|---|---|---|
| `false` | 在已授权输出边界原样返回/记录 | 始终保护 |
| `true` | 在所有已接入输出边界统一脱敏 | 始终保护 |

安全凭据包括 Bearer/JWT、authorization、access/refresh/id token、password、client secret 和 signature。它们不是可选的业务脱敏功能，不允许因总开关关闭而裸露。

## 2. 作用边界

总开关只控制输出表现，不修改权威业务事实：

```text
数据库 / 领域对象 / 事务事件 / 审计事实：保存原值
                         ↓
统一 SystemRedactionPolicy
                         ↓
API 展示 / 日志 / 导出 / 通知 / 外部交付：按开关输出
```

不得在数据库写入前做不可逆掩码，也不得用脱敏代替授权、字段级权限、加密、Secret 管理或数据最小化。

## 3. 当前已接入

- ECS JSON 日志的 message、MDC 和异常栈；
- 本地/测试普通文本日志的 message 和异常栈；
- 工单联系人姓名、手机号和服务地址投影；
- 自由文本中的手机号、VIN、地址和金额规则；
- 始终保护的凭据/令牌/签名规则。

## 4. 全系统完成门禁

“全系统脱敏已完成”必须同时满足以下条件：

1. 所有 API DTO 中的敏感字段使用统一策略，不允许模块私自实现 `mask*`/`redact*` 规则；
2. 报表、CSV/Excel/PDF 导出、文件元数据和下载说明接入统一策略；
3. 邮件、短信、站内通知和推送接入统一策略；
4. OEM/第三方出站 payload 按合同明确哪些字段可脱敏，禁止通用正则破坏协议签名和字段类型；
5. 审计查询、运营后台、异常中心和搜索结果接入统一策略；
6. 普通文本与结构化日志都验证开关开/关两种状态；
7. 每个输出边界都有开启态“不泄露”和关闭态“保持原值”的自动化测试。

当前提交建立统一策略并接入现有明确脱敏点，但不能仅凭一个布尔配置宣称所有未来功能自动完成脱敏。新增输出功能必须显式接入 `SystemRedactionPolicy` 并补双态测试。

## 5. 使用方式

本地原样业务日志：

```bash
SPRING_PROFILES_ACTIVE=local \
SERVICEOS_REDACTION_ENABLED=false \
./mvnw -pl serviceos-backend spring-boot:run
```

本地验证全局脱敏：

```bash
SPRING_PROFILES_ACTIVE=local \
SERVICEOS_REDACTION_ENABLED=true \
./mvnw -pl serviceos-backend spring-boot:run
```

生产 ECS JSON：

```bash
SERVICEOS_LOG_FORMAT=ecs \
SERVICEOS_REDACTION_ENABLED=true \
java -jar app.jar
```

配置值不是 `true` 或 `false` 时启动/日志处理失败关闭，避免拼写错误被静默解释为关闭脱敏。
