---
title: Admin 产品验收与里程碑状态
version: 0.1.0
status: Proposed
lastUpdated: 2026-07-20
---

# Admin 产品验收与里程碑状态

## 1. 为什么重新定义完成状态

过去“后端已实现”“页面能打开”“自动测试通过”经常被统一写成“已完成”，导致产品负责人看到的页面仍是技术调试界面。此后必须分别记录技术、前端、产品和质量状态。

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
- 功能测试通过，视觉尚未批准。

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
13. 产品负责人是否看过截图或实际页面并明确批准。

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
- 英文业务枚举。

允许例外必须精确到诊断组件、隐藏文件输入或测试夹具，不能通配整个 pages 目录。

## 6. 视觉验收

每张关键页面至少提供：

- 1440×1024 正常状态；
- 1280px 正常状态；
- 适用的空、错误、只读、无权限、冲突、Stale 或 Shadow 状态；
- 真实或脱敏业务样例；
- 主要弹窗、抽屉或专用流程。

视觉金标建立顺序：实现 → 人工产品审查 → 修复 → 批准 → 建立基线。

## 7. 六张母版批准门槛

以下页面必须由产品负责人逐张明确批准：

- 运营工作台；
- 用户管理；
- 项目管理；
- 工单中心；
- 工单详情；
- 项目履约配置。

母版未批准前，不得以其为依据批量迁移所有页面，也不得把当前截图批量设为金标。

## 8. 页面验收记录格式

```yaml
pageId: ADMIN.WORK_ORDER.LIST
route: /work-orders
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

## 9. GAP 规则

- `UI_DATA_GAP`：后端缺少显示名、聚合摘要、total 等数据；
- `DOMAIN_GAP`：缺少业务模型或运行规则；
- `EXTERNAL_BLOCKED`：Docker、OIDC 环境等外部阻塞。

GAP 必须说明当前安全降级和影响。不得用 GAP 掩盖页面布局、中文文案、组件使用或技术信息隔离未完成。