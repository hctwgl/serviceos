# 数据库设计、命名与描述规范

状态：Accepted  
数据库：PostgreSQL

## 1. 命名原则

数据库对象使用小写英文和下划线分隔，名称必须表达准确业务含义。禁止中文对象名、拼音、模糊缩写和无业务语义的通用名称。

推荐表名：

```text
work_order
work_order_assignment
service_appointment
review_case
correction_request
evidence_item
project_fulfillment_config
```

禁止：

```text
data
info
record
object
common
temp
table1
ext
```

推荐字段：

```text
appointment_start_at
appointment_end_at
sla_deadline_at
estimated_duration_minutes
upload_size_bytes
assigned_technician_id
created_at
created_by
```

禁止：

```text
time
date
value
type
status1
flag
data
info
extra
extend
remark1
```

布尔字段使用肯定含义，例如 `is_enabled`、`requires_review`、`allows_reschedule`。

## 2. 约束和索引命名

统一采用：

```text
pk_<table>
fk_<table>_<referenced_table>
uk_<table>_<business_fields>
ck_<table>_<rule>
idx_<table>_<indexed_fields>
```

示例：

```text
pk_work_order
fk_work_order_project
uk_work_order_tenant_order_number
ck_work_order_appointment_time_range
idx_work_order_tenant_status_created_at
```

数据库函数使用 `fn_` 前缀，触发器使用 `trg_` 前缀，并准确表达作用对象和结果。

## 3. 中文描述强制要求

以下正式对象必须具有中文描述：

- Schema；
- 表；
- 字段；
- 视图和物化视图；
- 主键、外键、唯一约束和检查约束；
- 索引；
- 枚举类型；
- 函数和存储过程；
- 触发器；
- 序列。

示例：

```sql
COMMENT ON TABLE work_order IS
'服务履约工单。记录一次从受理、预约、上门服务、资料提交、审核到完成的完整履约过程。';

COMMENT ON COLUMN work_order.order_number IS
'租户范围内对业务人员可见的工单编号。由系统生成，不允许修改，不等同于数据库主键。';

COMMENT ON COLUMN work_order.sla_deadline_at IS
'当前工单履约时限的截止时间。使用带时区时间类型，按照项目生效中的 SLA 规则计算。';

COMMENT ON CONSTRAINT ck_work_order_appointment_time_range
ON work_order IS
'预约结束时间必须晚于预约开始时间。';

COMMENT ON FUNCTION fn_work_order_recalculate_sla(uuid) IS
'根据工单所属项目、生效配置和当前业务阶段重新计算 SLA 截止时间。';
```

字段描述应根据需要说明：业务含义、数据来源、取值范围、单位、时区、可空语义、修改规则、敏感级别、生命周期和与其他字段的关系。

不合格描述：

```sql
COMMENT ON COLUMN work_order.status IS '状态';
COMMENT ON COLUMN work_order.data IS '数据';
COMMENT ON COLUMN work_order.created_at IS '创建时间';
```

## 4. 时间、金额和单位

- 业务时间统一使用带时区时间类型；
- 数据库存储和服务端处理必须明确 UTC 语义；
- 日期、时间点和持续时长不得混用；
- 持续时长字段必须在名称中写明单位；
- 金额必须使用确定精度的数值类型，并在字段名或描述中明确币种和最小单位；
- 禁止使用浮点数存储金额。

## 5. JSON 使用边界

JSON 仅允许用于：

- 外部系统原始报文归档；
- 版本化配置文档；
- 明确不参与核心查询的扩展信息；
- 有独立 Schema、版本号和校验规则的数据结构。

不得为减少建表、避免领域建模或绕过迁移而把核心业务数据放入 `data`、`extra`、`attributes`、`properties`、`payload` 或 `metadata`。

## 6. 函数和触发器边界

触发器只允许处理：

- 审计辅助；
- 更新时间维护；
- 数据完整性保护；
- 无法由标准约束表达的底层一致性要求。

派单、审核、整改、状态流转、SLA 变更和配置发布等核心业务必须由应用层领域命令显式执行，不得隐藏在触发器中。

所有函数和触发器必须说明：触发条件、输入、输出、修改的数据、异常行为和幂等语义。

## 7. 主键与业务编号

内部主键和业务可见编号必须分离：

- 内部主键用于引用和一致性；
- 业务编号用于搜索、沟通和展示；
- 产品页面不得以内部主键替代业务编号或业务名称；
- 业务编号必须定义作用域、生成规则、唯一性和不可变性。

## 8. 迁移规则

当前探索期不迁移旧开发数据。发现不合理结构时直接重建正确模型：

- 删除旧字段和旧表；
- 修改全部调用方；
- 重建 Seed 和测试夹具；
- 更新 OpenAPI 和生成客户端；
- 删除旧迁移说明和兼容脚本。

首次正式发布前应将探索期 Flyway 迁移压缩为清晰的新产品基线。不得为了支持可销毁开发数据而维持长期迁移负担。
