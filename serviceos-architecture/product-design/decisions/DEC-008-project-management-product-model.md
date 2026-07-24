# DEC-008：ServiceOS 项目管理与履约领域模型决策 V1.0

状态：Accepted

版本：1.0.0

日期：2026-07-24

## 1. 决策目标

ServiceOS 不是传统工单系统，而是新能源现场履约操作系统。

核心模型：

Customer → Project → Fulfillment Plan → Fulfillment Version →
Fulfillment Blueprint → Workflow / Task / Form / SLA / Evidence /
Settlement → WorkOrder Runtime

## 2. 核心原则

设计态负责定义规则：

-   Project
-   Fulfillment Plan
-   Version
-   Blueprint
-   Workflow
-   Task Template
-   Form Template
-   SLA Policy
-   Evidence Rule
-   Settlement Rule

运行态负责执行：

-   WorkOrder
-   Workflow Instance
-   Task Instance
-   Field Execution

## 3. Project 项目

Project 是合作项目的商业和运营边界。

一个 Customer 可以拥有多个 Project。

例如：

比亚迪： - 山东家充服务项目 - 上海家充服务项目 - 售后维修项目

Project 管理：

-   项目名称
-   客户
-   服务周期
-   服务区域
-   项目负责人
-   服务网络
-   责任组织
-   接口配置
-   结算体系

Project 不负责：

-   工单执行
-   现场任务
-   表单数据
-   证据数据

## 4. Fulfillment Plan 履约方案

履约方案表示：

一个项目针对某一种业务类型的完整履约方式。

例如：

比亚迪山东家充项目：

-   王朝车型 / 勘测安装
-   海洋车型 / 勘测安装
-   售后维修

包含：

-   适用范围
-   流程定义
-   阶段定义
-   任务模板
-   表单模板
-   证据规则
-   SLA规则
-   验收规则
-   结算规则
-   通知规则

不包含：

-   工单数量
-   完成率
-   实际执行数据

## 5. Fulfillment Version 履约版本

版本是完整履约规则的不可变快照。

例如：

比亚迪王朝勘测安装 V3

包含：

-   Workflow
-   Task Template
-   Form Template
-   Evidence Rule
-   SLA Policy
-   Settlement Rule

规则：

-   一个方案只能有一个生效版本
-   历史版本只读
-   新版本不修改历史工单

## 6. Blueprint 履约蓝图

Blueprint 是履约版本内部设计模型。

包含：

-   Workflow
-   Stage
-   Task Template
-   Form Template
-   Evidence Rule
-   SLA Policy
-   Settlement Rule
-   Notification Rule

## 7. Workflow 流程

Workflow 定义业务执行路径。

示例：

开始 → 客户联系 → 预约 → 现场勘测 → 审核 → 安装 → 验收 → 结束

## 8. Task Template 任务模板

定义谁负责什么。

包含：

-   任务名称
-   责任角色
-   执行主体
-   输入数据
-   输出结果
-   关联表单
-   关联证据
-   SLA
-   完成条件
-   异常策略

## 9. Form Template 表单模板

定义现场采集数据结构。

## 10. Evidence Rule 证据规则

定义任务完成需要的证明。

## 11. SLA Policy 时效规则

定义目标时间、预警时间、升级规则。

## 12. Settlement Rule 结算规则

定义履约后的商业结算逻辑。

## 13. WorkOrder 工单

WorkOrder 是履约方案版本的一次实际执行。

创建工单时必须绑定 Fulfillment Version。

## 14. 模块边界

履约方案页面：

关注规则设计：

-   方案
-   版本
-   流程
-   任务模板
-   表单
-   SLA
-   证据
-   结算

不展示：

-   工单数量
-   完成率
-   执行状态

工单管理页面：

关注执行：

-   当前阶段
-   当前任务
-   SLA状态
-   时间线
-   现场资料
-   异常
-   责任人

## 15. Agent 开发约束

禁止：

-   将履约方案设计成工单统计页面
-   将版本设计成修改日志
-   将流程设计成静态阶段列表
-   将任务设计成字段表
-   将运行数据混入配置页面

必须：

-   履约方案是规则资产
-   版本是冻结快照
-   工单是执行实例
-   Blueprint 是设计入口
-   页面围绕用户任务，而不是数据库对象
