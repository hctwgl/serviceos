---
title: Network Portal 核心页面母版与产品验收
version: 1.0.0
status: Accepted
lastUpdated: 2026-07-20
owner: Product Owner
---

# Network Portal 核心页面母版与产品验收

## 1. 目的

本文件将已批准的网点端方案 A 转化为页面母版、交互要求、数据需求和验收门禁。

核心原则：

> 网点端围绕“今天要处理什么、由谁负责、何时完成、有什么风险”组织，而不是围绕后端模块和数据库对象组织。

## 2. 核心页面清单

第一阶段必须完成并逐页验收：

1. 网点工作台；
2. 本网点工单列表；
3. 网点工单工作区；
4. 师傅分配与改派；
5. 预约协同；
6. 资料整改；
7. 师傅与资质；
8. 产能状态；
9. 异常中心。

其中前六页形成网点端黄金闭环。

---

# 母版一：网点工作台

## 3. 页面目标

让网点负责人无需跳转即可理解当日待办、师傅负载、预约、SLA 和异常。

## 4. 页面结构

```text
NetworkAppShell
→ 当前网点 + 日期范围
→ SummaryStrip
→ 今日任务时间轴
→ 待分配工单
→ 师傅状态与负载
→ 今日预约
→ SLA 预警
→ 快速操作
```

## 5. 概览指标

固定优先级：

1. 待分配工单；
2. 今日预约；
3. 进行中工单；
4. 即将超时；
5. 待整改；
6. 异常待处理。

每个数字必须：

- 说明统计范围和截至时间；
- 可点击进入对应预设视图；
- 不以昨日环比替代当前风险；
- 无数据时显示 0 和明确空状态，不隐藏卡片造成误解。

## 6. 今日任务时间轴

展示网点当日关键运营节奏：

- 早间待分配；
- 上午预约和上门；
- 下午预约高峰；
- 资料审核/整改截止；
- 日终复盘和未完成事项。

时间轴是业务日程摘要，不是虚构的流程节点。

## 7. 待分配工单

建议列：

- 工单号；
- 客户脱敏名称；
- 服务类型；
- 期望上门窗口；
- SLA 剩余；
- 风险；
- 操作。

主操作：`分配师傅`。

点击工单号进入工单工作区；点击分配打开右侧分配抽屉。

## 8. 师傅状态与负载

展示：

- 姓名；
- 当前状态；
- 今日已接/上限；
- 负载比例；
- 未来最近预约；
- 资质风险；
- 是否可接单。

“负载”必须使用服务端统计口径，不能由前端简单用任务数猜测。

## 9. 今日预约与 SLA

今日预约展示：时间、客户、任务、师傅和状态。

SLA 预警展示：工单、剩余时间、级别、阻塞原因和建议动作。

## 10. 工作台验收

必须证明：

- 数字可下钻且结果一致；
- 只显示当前 Network Context；
- 改派后的工单及时失效；
- 待分配和 SLA 风险排序来自服务端；
- 不出现总部价格或其他网点信息；
- 1440×1024 首屏能看到待分配、预约、师傅负载和 SLA；
- 空、错、无权限、投影延迟和异步处理中状态完整。

---

# 母版二：本网点工单列表

## 11. 页面目标

用于批量查找、筛选和进入本网点当前负责的工单，不承担完整处理流程。

## 12. 筛选

- 关键词；
- 工单状态；
- 当前任务；
- 师傅；
- 预约日期；
- SLA 风险；
- 资料/整改状态；
- 服务类型；
- 区域。

支持保存视图：

- 待分配；
- 今日上门；
- 即将超时；
- 待整改；
- 未联系成功；
- 进行中。

## 13. 表格列

- 工单号；
- 客户脱敏信息；
- 服务产品；
- 区域；
- 当前阶段；
- 当前任务；
- 当前师傅；
- 预约窗口；
- 资料/整改；
- SLA；
- 更新时间；
- 操作。

不展示内部配置版本、其他网点历史、总部负责人和价格。

## 14. 列表验收

- 所有筛选可组合；
- 分页稳定；
- 总数和当前页数据来自服务端；
- 主列固定；
- 改派后旧 Context 查询和深链均失败关闭；
- 导出按 Capability 和字段策略执行；
- 表格在 1280px 可用。

---

# 母版三：网点工单工作区

## 15. 页面目标

将本网点处理一张工单所需的 Workflow、Task、Appointment、Evidence、Correction、SLA 和联系记录组合为一个工作区。

## 16. 对象头

展示：

- 工单号；
- 当前状态；
- SLA 剩余；
- 客户脱敏信息；
- 服务地址；
- 服务类型；
- 当前责任网点；
- 当前师傅；
- 来源系统。

右侧主操作根据 allowed-actions 显示：

- 分配师傅；
- 改派师傅；
- 联系客户；
- 修改预约；
- 查看整改；
- 代补资料；
- 上报异常。

## 17. 履约进度

以冻结 Workflow 的业务阶段呈现，例如：

```text
已接入
→ 待分配（当前）
→ 已预约
→ 上门中
→ 资料审核
→ 完成
```

阶段和当前 Task 必须分别表达；不得把 Task 状态伪装成工单状态。

## 18. 卡片布局

主区：

- 当前任务；
- 预约信息；
- 师傅分配；
- 资料与证据；
- 工单动态。

右侧上下文：

- 风险预警；
- 异常备注；
- 联系记录；
- 下一步指引。

卡片只展示支持当前决策的信息，不重复对象头。

## 19. 当前任务

展示：

- 任务名称；
- 创建时间；
- 发起人；
- 期望完成时间；
- 当前责任；
- 阻塞原因；
- 允许动作。

## 20. 工单动态

时间线至少包括：

- 接单；
- 分配/改派；
- 联系；
- 预约；
- 到场；
- 资料提交；
- 审核/整改；
- 异常和解决；
- 完成。

时间线显示业务名称和操作者，不显示事件类名。

## 21. 工作区验收

- 首屏能说明当前状态和下一步；
- 所有动作来自 allowed-actions；
- 电话、地址和资料按字段策略展示；
- 已改派或失权时立即只读/拒绝；
- 操作冲突展示最新事实；
- 不展示其他网点和总部敏感内容；
- 关键动作有影响说明和操作进度。

---

# 母版四：师傅分配与改派

## 22. 交互形式

默认使用工单工作区右侧宽抽屉；批量调度可以进入独立页面，但必须保留工单上下文。

## 23. 抽屉结构

```text
标题：分配师傅 / 改派师傅
→ 可分配 / 最近分配 Tab
→ 搜索
→ 技能、区域、可用时间、资质、距离筛选
→ 候选师傅卡片
→ 已选师傅
→ 影响与冲突摘要
→ 确认按钮
```

## 24. 候选师傅卡

展示：

- 头像、姓名；
- 技能和资质标签；
- 可用/忙碌/受限；
- 今日已接/容量；
- 时间冲突；
- 距离；
- 当前最近任务；
- 推荐解释。

禁止展示内部评分公式和其他网点师傅。

## 25. 改派影响

改派前必须说明：

- 原师傅；
- 新师傅；
- 预约是否冲突；
- 原师傅离线工作包将失效；
- 未上传本地资料如何处理；
- 是否通知客户和师傅；
- 是否需要原因或审批。

## 26. 提交状态

```text
确认提交
→ 分配处理中
→ ServiceAssignment 激活
→ TaskAssignment 激活
→ 完成
```

任一关键步骤失败时，页面显示真实状态和恢复路径。

## 27. 分配验收

- 候选来自服务端授权和规则；
- 无候选时解释原因；
- 资质、容量和冲突可解释；
- 不允许重复提交；
- saga 未完成不显示成功；
- 改派后旧师傅和旧网点权限失效；
- 原因、审批和审计完整。

---

# 母版五：预约协同

## 28. 页面结构

- 今日/未来预约日历；
- 待联系任务；
- 联系记录；
- 当前预约 revision；
- 可选时间窗口；
- 师傅日程冲突；
- 用户确认渠道；
- 改约原因；
- 操作结果。

## 29. 状态

- 待联系；
- 已联系待确认；
- 已预约；
- 改约中；
- 用户拒绝；
- 无法联系；
- 已取消；
- 已爽约。

预约草稿不等于已确认。

## 30. 预约验收

- 联系记录只追加；
- 操作者和渠道清晰；
- revision/ETag 冲突不覆盖；
- 时间冲突可见；
- 通知失败不回滚预约事实；
- 无法联系使用标准原因；
- 网点代约不伪装成师傅操作。

---

# 母版六：资料整改

## 31. 队列

展示：

- 工单；
- 整改轮次；
- 被驳回资料；
- 驳回原因；
- 正确示例；
- 当前责任；
- 截止时间；
- SLA；
- 是否允许网点代补。

## 32. 代补流程

```text
选择整改项
→ 查看原版本和驳回原因
→ 选择代办师傅
→ 填写代补原因
→ 拍摄/上传
→ 服务端校验
→ 创建新 EvidenceRevision
→ 提交整改
```

页面必须显示 actualUploader 和 onBehalfOf 的区别。

## 33. 整改验收

- 只处理被驳回项；
- 已通过资料只读；
- 新资料形成新版本；
- 没有 Capability 或资料不允许代补时不显示动作；
- 代补关系、设备、时间和原因审计完整；
- 不能直接修改师傅原表单字段；
- 上传状态和最终提交状态不混淆。

---

# 母版七：师傅与资质

## 34. 师傅列表

展示：

- 姓名；
- 账号绑定；
- 启用状态；
- 服务状态；
- 技能；
- 服务区域；
- 资质有效期；
- 当前任务量；
- 最近同步。

## 35. 资质

展示：

- 资质类型；
- 当前状态；
- 有效期；
- 到期提醒；
- 审核状态；
- 适用服务类型；
- 限制说明。

网点提交资料不等于总部审核通过。

## 36. 人员变更验收

- 停用前分析未完成任务和预约；
- 必须给出重新分配计划；
- 资质过期阻止新分配但不静默终止已开始现场任务；
- 隐私字段最小展示；
- 多账号/关系异常有明确提示。

---

# 母版八：产能状态

## 37. 展示

按服务类型展示：

- 当前在途；
- 容量上限；
- 已预占；
- 可用余量；
- 派单状态；
- 停派原因；
- 有效期；
- 近期趋势。

不得展示总部内部评分和其他网点详细数据。

## 38. 调整申请

网点只能发起申请：

- 原值；
- 目标值；
- 生效期限；
- 原因；
- 证明材料；
- 审批状态。

不能直接修改 CapacityBucket。

---

# 母版九：异常中心

## 39. 异常类型

- 师傅未分配；
- 预约冲突；
- 联系失败；
- 资料整改超时；
- 资质到期；
- 关键通知失败；
- 分配/改派处理中断；
- 投影延迟；
- 网点可处理的其他运营异常。

## 40. 处理方式

异常页面必须深链到原领域动作，例如分配师傅、修改预约、补资料或更新资质。

不得提供没有业务效果的“标记已处理”按钮。

---

# 产品完成门禁

## 41. 状态要求

网点端页面只有同时满足以下条件，才允许标记完成：

```text
真实 API 可用
+ Network Context 与范围验证通过
+ 前端接入完成
+ 符合方案 A 母版
+ 正常/空/错/无权限/冲突/异步状态完整
+ 自动化测试通过
+ 人工视觉审查通过
+ 可访问性审查通过
+ 产品负责人批准
```

## 42. 黄金链路验收

真实网点负责人必须能够独立完成：

```text
查看待分配工单
→ 打开工单工作区
→ 选择合适师傅
→ 完成分配
→ 联系客户并预约
→ 跟踪上门
→ 处理资料整改
→ 查看最终完成状态
```

全过程不得要求复制内部 ID、切换到 Admin、查看原始 JSON 或使用接口工具。

## 43. 视觉验收

至少提供：

- 1440×1024 网点工作台；
- 1440×1024 工单工作区 + 分配师傅抽屉；
- 1280px 关键页面；
- 平板横屏工作台；
- 空、错、无权限、改派失效和异步处理中截图；
- 真实或脱敏业务数据。

产品负责人批准真实实现后，才能建立视觉回归金标。

## 44. UI_DATA_GAP

缺少以下数据时必须补服务端读模型，不能前端猜测：

- 待分配总数；
- 师傅真实负载和容量；
- 冲突原因；
- SLA 剩余与阻塞；
- 推荐解释；
- 当前责任链；
- 资料完成度；
- 改派影响；
- allowed-actions 和阻塞原因；
- 异步 Operation 状态。

## 45. M390 验收记录（工作台 + 分配抽屉）

```yaml
pageId: NETWORK.WORKBENCH
route: /network-portal/workbench
visualProfile: CLASSIC_PROFESSIONAL_COLLABORATION
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - UI_DATA_GAP: 经纬度/路网米制距离与数值推荐评分未就绪（开放任务/资质 M407、日程冲突 M408、行政区距离亲和 M410、事实型推荐解释 M412 已交付）
  - CONTENT_GAP: 完整预约日历视图未交付（今日预约时间轴/列表已由 M411 交付）
```

```yaml
pageId: NETWORK.TECHNICIAN.ASSIGN
route: /network-portal/workbench#assign-drawer
visualProfile: CLASSIC_PROFESSIONAL_COLLABORATION
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - UI_DATA_GAP: 经纬度/路网米制距离与数值推荐评分未就绪；行政区距离亲和已由 M410、事实型推荐解释已由 M412 交付
```

## 46. M391 验收记录（工单工作区 + 预约协同）

```yaml
pageId: NETWORK.WORKORDER.WORKSPACE
route: /network-portal/work-orders/:id
visualProfile: CLASSIC_PROFESSIONAL_COLLABORATION
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - UI_DATA_GAP: 客户/地址脱敏读模型未就绪（师傅日程冲突已由 M408、事实型推荐解释已由 M412 在分配抽屉交付）
  - CONTENT_GAP: 预约日历视图未交付
```

## 47. M392 验收记录（资料整改 + 异常中心）

```yaml
pageId: NETWORK.CORRECTION.QUEUE
route: /network-portal/corrections
visualProfile: CLASSIC_PROFESSIONAL_COLLABORATION
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - UI_DATA_GAP: 正确示例图、代补允许标志、整改截止 SLA 专用读模型
```

```yaml
pageId: NETWORK.EXCEPTION.QUEUE
route: /network-portal/exceptions
visualProfile: CLASSIC_PROFESSIONAL_COLLABORATION
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - DOMAIN_GAP: Portal ACK/resolve/decide 仍未批准交付
```

## 48. M396 验收记录（师傅与产能）

```yaml
pageId: NETWORK.TECHNICIAN.LIST
route: /network-portal/technicians
visualProfile: CLASSIC_PROFESSIONAL_COLLABORATION
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - UI_DATA_GAP: 技能、服务区域、当前任务量、最近同步读模型未就绪
```

```yaml
pageId: NETWORK.CAPACITY
route: /network-portal/capacity
visualProfile: CLASSIC_PROFESSIONAL_COLLABORATION
technicalStatus: RUNTIME_CONNECTED
frontendStatus: FRONTEND_COMPLETE
productStatus: READY_FOR_REVIEW
qualityStatus:
  test: TEST_PASSED
  visual: VISUAL_NOT_REVIEWED
  accessibility: A11Y_NOT_REVIEWED
productOwnerDecision: null
knownGaps:
  - DOMAIN_GAP: 产能调整申请写流程尚未产品化交付
```
