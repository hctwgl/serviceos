# 中文友好开发与代码注释规范

状态：Accepted  
生效日期：2026-07-21

## 1. 语言边界

面向人的信息必须使用准确、完整、自然的简体中文，包括：

- 产品界面和操作提示；
- 后端业务注释和 Javadoc；
- OpenAPI 接口与字段说明；
- 异常标题、错误详情和处理建议；
- 应用日志事件说明；
- 数据库对象描述；
- 配置项说明；
- 运维告警；
- 测试名称和失败原因；
- 开发、架构和产品文档。

代码类名、方法名、字段名、错误代码和数据库对象名使用规范英文。禁止拼音命名、中文变量名和中英文混合标识。

## 2. 命名要求

名称必须表达明确领域含义。

推荐：

```java
WorkOrder
ServiceAppointment
CorrectionRequest
assignTechnician()
submitForReview()
calculateSlaDeadline()
```

禁止：

```java
Data
Info
Item
CommonService
Util
handle()
processData()
GongDan
paiDan()
```

行业通用缩写可以使用，例如 `ID`、`URL`、`HTTP`、`API`、`SLA`、`GPS`、`OIDC` 和 `JSON`。其他缩写必须避免。

## 3. 必须添加中文注释的位置

以下代码必须有准确中文说明：

- 聚合根和领域实体；
- 领域服务和应用服务；
- 公共接口；
- 状态机；
- 权限与数据范围判断；
- SLA 计算；
- 审核和整改规则；
- 幂等、并发和事务处理；
- 外部系统集成；
- 数据脱敏、加密和安全逻辑；
- 非直观算法；
- 临时性平台限制。

注释应说明：为什么这样设计、业务前置条件、不可违反的约束、失败后如何恢复，以及为什么不能采用看似更简单的实现。

推荐：

```java
/**
 * 提交工单进入审核流程。
 *
 * <p>提交前必须完成全部必填作业步骤，并登记全部待上传资料。
 * 本方法只创建审核申请，不代表审核已经通过。</p>
 *
 * @param tenantId 租户标识
 * @param workOrderId 工单标识
 * @param expectedVersion 客户端读取工单时的版本号，用于检测并发修改
 * @throws WorkOrderStateConflictException 当前工单状态不允许提交
 */
public void submitForReview(
        TenantId tenantId,
        WorkOrderId workOrderId,
        long expectedVersion) {
}
```

禁止逐行翻译代码：

```java
// 判断状态是否为已完成
if (status == COMPLETED) {
    // 返回 true
    return true;
}
```

正确写法：

```java
// 已完成属于不可逆终态。重复回调只能返回原结果，不能重新进入履约流程。
if (status == COMPLETED) {
    return true;
}
```

## 4. 注释质量

禁止：

- 无业务意义的自动生成注释；
- 与方法名完全重复的注释；
- 与代码不一致的过期注释；
- 没有责任人、触发条件和处理范围的 `TODO`；
- “临时处理”“以后优化”等不可验证表述；
- 为旧实现保留的兼容说明。

探索期废弃设计直接删除，由 Git 历史保存。

## 5. OpenAPI 中文说明

每个接口必须提供中文：

- `summary` 和 `description`；
- 请求参数和字段说明；
- 响应字段说明；
- 权限要求；
- 幂等语义；
- 并发冲突语义；
- 业务错误和下一步操作；
- 枚举的中文业务含义。

接口传输代码保持英文稳定值，面向人的标签使用中文：

```json
{
  "code": "WAITING_FOR_ASSIGNMENT",
  "label": "待派单"
}
```

不得仅依赖字段名自动生成接口文档。

## 6. 测试中文化

测试名称应描述完整业务行为：

```java
@Test
@DisplayName("工单已经完成时，重复提交完成命令应返回原操作结果")
void shouldReturnPreviousResultWhenCompletionCommandIsRepeated() {
}
```

断言应提供中文业务语境：

```java
assertThat(actualStatus)
    .as("审核通过后，工单应进入等待车企回传状态")
    .isEqualTo(WorkOrderStatus.WAITING_FOR_EXTERNAL_RECEIPT);
```

禁止使用 `test1`、`testSuccess`、`normalCase`、`errorCase` 等名称。

## 7. 文档和术语

正式文档统一使用简体中文。首次出现专业术语时可以使用“中文（English）”形式。

仓库必须维护统一产品术语，至少覆盖工单、项目、网点、师傅、预约、履约、审核、整改、资料、派单、改派、回传、配置版本、允许动作和审计记录。

同一业务概念不得在不同模块中使用不同中文译法。
