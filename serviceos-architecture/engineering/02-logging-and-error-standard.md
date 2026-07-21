# 日志、异常与错误模型规范

状态：Accepted  
生效日期：2026-07-21

## 1. 错误模型

每个业务错误必须包含：

- 稳定英文错误代码；
- 中文错误标题；
- 中文详细说明；
- 必要的结构化上下文；
- 是否允许重试；
- 用户或运维人员的下一步操作。

示例：

```json
{
  "code": "WORK_ORDER_STATUS_CONFLICT",
  "title": "工单状态已发生变化",
  "detail": "当前工单已经由其他人员完成派单，请刷新后查看最新处理结果。",
  "retryable": false
}
```

程序不得根据中文错误消息进行判断，必须使用异常类型或错误代码。

禁止使用以下模糊信息：

- 操作失败；
- 参数错误；
- 数据不存在；
- 系统异常；
- 保存失败；
- 状态非法。

错误信息必须说明业务对象、失败原因和下一步操作。

## 2. 日志语言和结构

日志事件说明使用中文，结构化检索字段使用稳定英文。

推荐字段：

- `tenantId`；
- `workOrderId`；
- `projectId`；
- `networkId`；
- `technicianId`；
- `operatorId`；
- `requestId`；
- `traceId`；
- `operationId`；
- `errorCode`；
- `durationMs`；
- `attempt`。

示例：

```java
log.info(
    "工单已经指派给师傅：tenantId={}, workOrderId={}, technicianId={}, operatorId={}",
    tenantId,
    workOrderId,
    technicianId,
    operatorId
);
```

```java
log.error(
    "向车企回传工单结果失败：tenantId={}, workOrderId={}, integrationCode={}, attempt={}",
    tenantId,
    workOrderId,
    integrationCode,
    attempt,
    exception
);
```

一条有效日志至少应说明：发生了什么、作用于哪个业务对象、由谁或什么触发、结果是什么、失败原因是什么、是否会重试。

禁止只记录“开始处理”“处理完成”“处理失败”。

## 3. 日志级别

- `INFO`：重要业务状态变化、命令受理、操作完成和外部交互结果；
- `WARN`：业务拒绝、可恢复异常、重试、降级为明确错误状态；
- `ERROR`：未完成业务目标、数据一致性风险、外部系统最终失败和不可自动恢复异常；
- `DEBUG`：开发诊断细节，不承载必须审计的业务事实。

正常业务校验失败不得全部记录为 `ERROR`；系统异常也不得降为 `INFO`。

## 4. 异常捕获

禁止：

```java
try {
    return repository.findById(id);
} catch (Exception exception) {
    log.warn("查询失败");
    return Optional.empty();
}
```

禁止捕获异常后：

- 返回空集合；
- 返回默认对象；
- 返回成功；
- 继续执行不完整流程；
- 调用旧接口；
- 隐藏并发冲突；
- 把未知状态当作正常状态。

捕获异常必须满足至少一项：

- 增加明确业务上下文后重新抛出；
- 转换为稳定业务错误；
- 执行受控补偿并保留失败事实；
- 按明确重试策略提交到可靠队列。

第三方异常可以保留原始堆栈，但必须增加中文业务上下文。

## 5. 敏感信息

日志不得记录：

- Access Token 和 Refresh Token；
- 密码；
- 完整手机号；
- 身份证号和银行卡号；
- 完整客户地址；
- 客户影像和签名；
- Cookie 和 Authorization 请求头；
- 未脱敏的外部请求或响应正文。

定位问题时使用脱敏值、业务对象标识、摘要或哈希。

## 6. 用户错误与系统诊断分离

面向用户的信息必须使用业务语言，不暴露：

- SQL；
- Java 类名；
- 堆栈；
- UUID；
- 内部字段名；
- 英文枚举；
- 第三方 SDK 原始异常。

系统诊断信息通过 `traceId`、`operationId` 和受权限控制的诊断工具关联，不直接堆进业务页面。

## 7. 重试与幂等

错误模型必须明确区分：

- 不可重试的业务拒绝；
- 可以原请求重试的临时失败；
- 必须刷新数据后重新操作的并发冲突；
- 已受理、需要查询操作结果的异步命令。

任何重试都必须有幂等键、次数上限、退避策略和最终失败处理。不得使用无限重试或静默吞掉最终失败。
