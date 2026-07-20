---
title: Admin 产品验收与里程碑状态
version: 0.2.0
status: Proposed
lastUpdated: 2026-07-20
approvedVisualProfile: 方案 A｜经典专业风
---

# Admin 产品验收与里程碑状态

## 1. 为什么重新定义完成状态

过去“后端已实现”“页面能打开”“自动测试通过”经常被统一写成“已完成”，导致产品负责人看到的页面仍是技术调试界面。此后必须分别记录技术、前端、产品和质量状态。

产品负责人已经批准 Admin 的总体视觉方向为 **方案 A｜经典专业风**。该批准只锁定产品语言和核心页面结构，不代表任何当前代码页面自动获得 `PRODUCT_ACCEPTED` 或 `VISUAL_APPROVED`。

视觉事实源：[经典专业风视觉与页面基线](12-classic-professional-visual-baseline.md)。

## 2. 四类状态

### 2.1 技术状态

- `TECH_NOT_STARTED`
- `BACKEND_IMPLEMENTED`
- `API_AVAILABLE`
- `RUNTIME_CONNECTED`

### 2.2 前端状态

- `FRONTEND_NOT_STARTED`
- `FRONTEND_SHELL_ONLY`
- `FRONTEND_CONNECTED`
- `FRONTEND_COMPLETE`

`FRONTEND_SHELL_ONLY` 表示仅接入 AppShell、PageContainer 或基础路由，不得称为页面产品化完成。

`FRONTEND_COMPLETE` 还要求页面实现完整正常、空、错、无权限、只读和冲突状态，并使用真实服务端 allowed-actions 和业务 DTO。

### 2.3 产品状态

- `PRODUCT_NOT_DESIGNED`
- `PRODUCT_DESIGNED`
- `READY_FOR_REVIEW`
- `PRODUCT_ACCEPTED`
- `PRODUCT_REJECTED`

### 2.4 质量状态

- `TEST_NOT_RUN`
- `TEST_PASSED`
- `VISUAL_NOT_REVIEWED`
- `VISUAL_APPROVED`
- `A11Y_NOT_REVIEWED`
- `A11Y_APPROVED`

## 3. “已完成”的唯一条件

面向产品负责人报告“已完成”必须同时满足：

```text
API_AVAILABLE 或 RUNTIME_CONNECTED
+ FRONTEND_COMPLETE
+ PRODUCT_ACCEPTED
+ TEST_PASSED
+ VISUAL_APPROVED
+ A11Y_APPROVED
```

否则使用准确描述，例如：

- 技术能力已实现，前端未接入；
- 前端已接入，页面待产品审查；
- 产品设计已批准，后端数据缺口阻塞；
- 功能测试通过，视觉尚未批准；
- 已符合经典专业风，但业务流程仍不完整。

## 4. 单页产品验收清单

一张页面标记 `PRODUCT_ACCEPTED` 前必须回答：

1. 目标角色是谁；
2. 页面核心任务是什么；
3. 主要操作是否唯一且清晰；
4. 用户是否无需理解内部 ID、API 或命令名；
5. 页面是否使用正确母版/页面模式；
6. 列表字段是否支持业务比较和决策；
7. 详情首屏是否表达状态、责任、SLA、风险和下一步；
8. 创建/编辑是否使用标准表单，而非列表常驻长表单；
9. 复杂操作是否使用正确容器；
10. 无权限、空、错、冲突是否可理解；
11. 1440px 和 1280px 是否可用；
12. 键盘和 200% 缩放是否可用；
13. 页面是否符合经典专业风视觉基线；
14. 页面是否使用真实业务数据和服务端允许动作；
15. 产品负责人是否看过截图或实际页面并明确批准。

## 5. 自动防回退门禁

正式页面源代码和 E2E 应扫描以下内容：

- 普通页面原生 button/input/select/table；
- 旧 QueueTable；
- 长 UUID 作为可见正文；
- `/api/v1/`；
- principalIds、sourceId、networkIds、regionCodes 作为用户标签；
- If-Match、Idempotency-Key；
- assign-candidates、revise-scope-relations 等命令文案；
- ISO 纳秒时间；
- 英文业务枚举；
- Manifest/document JSON 作为普通配置界面；
- 页面自行解析 Workflow/Task/Bundle 内部结构并形成第二套业务解释；
- 页面自行根据角色名或路由猜测权限；
- 不受 Token 管理的任意主色、圆角和阴影值。

允许例外必须精确到诊断组件、隐藏文件输入或测试夹具，不能通配整个 pages 目录。

## 6. 视觉验收

每张关键页面至少提供：

- 1440×1024 正常状态；
- 1280px 正常状态；
- 适用的空、错误、只读、无权限、冲突、Stale 或 Shadow 状态；
- 真实或脱敏业务样例；
- 主要弹窗、抽屉或专用流程；
- 与经典专业风基线的对照说明。

### 6.1 经典专业风检查项

- 白色/浅色应用壳和左侧导航；
- 标准企业蓝用于主操作和选中状态；
- 页面标题、摘要、主工作区和右侧上下文层级清楚；
- 信息密度适合长期运营，不通过极小字体实现；
- 卡片使用细边框和克制阴影；
- 主操作突出，次操作不抢占；
- 复杂配置使用目录、画布、属性、版本和影响分析；
- 不使用营销插画、夸张渐变、霓虹、玻璃拟态和大面积深色；
- 不暴露技术 ID、JSON 和英文枚举；
- 状态、风险和版本不只依赖颜色。

视觉金标建立顺序：实现 → 人工产品审查 → 修复 → 批准 → 建立基线。

概念效果图已批准为方向，不得直接作为自动视觉金标。正式金标必须来自真实实现页面。

## 7. 母版与核心配置页批准门槛

以下页面必须由产品负责人逐张明确批准：

- 运营工作台；
- 用户管理；
- 项目管理；
- 工单中心；
- 工单详情；
- 项目履约配置中心；
- 工作流设计器；
- 任务模板中心。

其中以下四页已有概念方向批准：

1. 项目履约配置中心；
2. 工作流设计器；
3. 任务模板中心；
4. 工单详情。

“概念方向批准”对应 `PRODUCT_DESIGNED`，不等于实际实现 `PRODUCT_ACCEPTED`。

母版未批准前，不得以其为依据批量迁移所有页面，也不得把当前截图批量设为金标。

## 8. 四个已批准方向页面的专项验收

### 8.1 项目履约配置中心

必须验证：

- 项目、已发布版本、草稿、工单类型和使用中工单一眼可见；
- 配置包概览、工单类型配置表、影响分析和版本时间线完整；
- 新旧工单版本影响说明准确；
- 发布使用真实差异和服务端影响分析；
- 不显示 Manifest JSON。

### 8.2 工作流设计器

必须验证：

- 三栏结构成立；
- 节点、条件、汇合、异常和结束清晰；
- 节点引用 Task、表单、资料和 SLA 可见；
- 校验错误能定位节点；
- 发布受 allowed-actions 和校验结果控制；
- 键盘或等价列表方式可操作节点。

### 8.3 任务模板中心

必须验证：

- 分类、列表和详情形成统一工作区；
- 执行角色、分配、表单、资料、SLA、动作和升级完整；
- Workflow 引用关系可追踪；
- 版本状态、复制、比较和发布清晰；
- 业务名称为主，模板编码为次。

### 8.4 工单详情

必须验证：

- WorkOrder、Workflow、Task、资料、审核、责任、SLA 和外部回传在一个页面中形成完整业务解释；
- 当前阶段和当前 Task 分开表达；
- 主要操作由服务端 allowed-actions 驱动；
- 右侧风险、责任链、外部集成和最近时间线支持决策；
- 任意业务争议能追溯到流程、任务、资料、审核或配置来源。

## 9. 页面验收记录格式

```yaml
pageId: ADMIN.WORK_ORDER.LIST
route: /work-orders
visualProfile: CLASSIC_PROFESSIONAL
technicalStatus: API_AVAILABLE
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps: []
```

### 9.1 M385 切片 A 记录（项目履约配置）

```yaml
pageId: ADMIN.PROJECT.FULFILLMENT.LIST
route: /projects/:id/fulfillment-profiles
visualProfile: CLASSIC_PROFESSIONAL
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - UI_DATA_GAP: 使用中工单数跨模块读模型未就绪
  - DOMAIN_GAP: 左导航工作流/任务模板等分区仍为占位
```

```yaml
pageId: ADMIN.PROJECT.FULFILLMENT.CREATE
route: /projects/:id/fulfillment-profiles/create
visualProfile: CLASSIC_PROFESSIONAL
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps: []
```

```yaml
pageId: ADMIN.PROJECT.FULFILLMENT.PUBLISH
route: /projects/:id/fulfillment-profiles/:profileId/publish
visualProfile: CLASSIC_PROFESSIONAL
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps: []
```

```yaml
pageId: ADMIN.PROJECT.FULFILLMENT.EDIT
route: /projects/:id/fulfillment-profiles/:profileId/edit
visualProfile: CLASSIC_PROFESSIONAL
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - DOMAIN_GAP: Workflow/Task 实体选择器可继续深化与设计器联动
```

### 9.2 M389 记录（工单详情统一履约工作区）

```yaml
pageId: ADMIN.WORKORDER.WORKSPACE
route: /work-orders/:id
visualProfile: CLASSIC_PROFESSIONAL
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - UI_DATA_GAP: 客户/地址/联系方式正式脱敏读模型仍缺
  - UI_DATA_GAP: 表单资料缩略图与完整审核记录产品化未完成
```

### 9.3 M397 记录（用户管理）

```yaml
pageId: ADMIN.USER.DIRECTORY
route: /users
visualProfile: CLASSIC_PROFESSIONAL
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - UI_DATA_GAP: 失败登录/设备指纹（成功最近登录已由 M403 交付）
```

```yaml
pageId: ADMIN.USER.DETAIL
route: /users/:id
visualProfile: CLASSIC_PROFESSIONAL
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - UI_DATA_GAP: 跨聚合业务时间线与操作者显示名（主体变更时间线已由 M405 关闭主路径）
```

### 9.4 M398 记录（项目管理 / 工作台）

```yaml
pageId: ADMIN.PROJECT.LIST
route: /projects
visualProfile: CLASSIC_PROFESSIONAL
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - UI_DATA_GAP: 完整行政区名称树 / 独立车企主数据目录
```

```yaml
pageId: ADMIN.WORKBENCH
route: /workbench
visualProfile: CLASSIC_PROFESSIONAL
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - UI_DATA_GAP: 关注项目待办/SLA 角标聚合（关注列表本身已由 M401 交付）
```

### 9.5 M399 记录（项目列表方案聚合）

```yaml
pageId: ADMIN.PROJECT.LIST
route: /projects
visualProfile: CLASSIC_PROFESSIONAL
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - UI_DATA_GAP: 车企/区域/网点实体选择器（方案聚合已由 M399 关闭；选择器由 M400 关闭主路径）
```

### 9.6 M400 记录（项目实体选择器）

```yaml
pageId: ADMIN.PROJECT.LIST
route: /projects
visualProfile: CLASSIC_PROFESSIONAL
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - UI_DATA_GAP: 完整行政区名称树 / 独立车企主数据目录（聚合选择器已交付）
```

### 9.7 M401 记录（关注项目）

```yaml
pageId: ADMIN.WORKBENCH
route: /workbench
visualProfile: CLASSIC_PROFESSIONAL
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - UI_DATA_GAP: 关注项目上的待办/SLA 角标聚合
```

### 9.8 M402 记录（用户登记与目录摘要）

```yaml
pageId: ADMIN.USER.DIRECTORY
route: /users
visualProfile: CLASSIC_PROFESSIONAL
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - UI_DATA_GAP: 最近登录列表读模型（已由 M403 关闭）
```

### 9.9 M403 记录（最近登录）

```yaml
pageId: ADMIN.USER.DETAIL
route: /users/:id
visualProfile: CLASSIC_PROFESSIONAL
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - UI_DATA_GAP: 失败登录与设备指纹尚未交付
```

### 9.10 M404 记录（组织任职）

```yaml
pageId: ADMIN.USER.DETAIL
route: /users/:id
visualProfile: CLASSIC_PROFESSIONAL
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - UI_DATA_GAP: 完整变更审计时间线（已由 M405 关闭主体范围）
```

### 9.11 M405 记录（变更时间线）

```yaml
pageId: ADMIN.USER.DETAIL
route: /users/:id
visualProfile: CLASSIC_PROFESSIONAL
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - UI_DATA_GAP: 跨聚合业务时间线与操作者显示名
```

## 10. GAP 规则

- `UI_DATA_GAP`：后端缺少显示名、聚合摘要、total、差异、影响、引用关系或 allowed-actions 等页面需要的数据；
- `DOMAIN_GAP`：缺少业务模型或运行规则；
- `EXTERNAL_BLOCKED`：Docker、OIDC 环境等外部阻塞；
- `VISUAL_GAP`：页面结构或视觉语言未达到经典专业风基线；
- `CONTENT_GAP`：中文名称、帮助、状态解释或错误文案缺失。

GAP 必须说明当前安全降级和影响。不得用 GAP 掩盖页面布局、中文文案、组件使用、技术信息隔离或真实业务流程未完成。
