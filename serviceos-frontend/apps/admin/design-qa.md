# Admin A+ 设计验收记录（Sprint-003 前基线）

参考资产：

- `serviceos-architecture/assets/product/admin/a-plus-gold/a-plus-overview-1672x941.png`
- `serviceos-architecture/assets/product/admin/a-plus-gold/work-order-workspace-1487x1058.png`

## 自动化工程门禁

- [x] Node 22 + pnpm 单锁文件安装
- [x] ESLint
- [x] TypeScript
- [x] 快速单元测试
- [x] 生产构建
- [x] 无 Playwright、视觉截图或浏览器 E2E 命令

## Chrome 人工产品验收

- [x] 真实 OIDC 登录
- [x] Admin 工作台真实数据
- [x] 工单中心查询并进入工作区
- [x] 工单工作区正常状态
- [x] 分配责任网点成功
- [x] 只读账号登录及中文无权限状态
- [ ] 无候选
- [ ] 候选过期
- [ ] 并发冲突
- [x] 1440 × 900 与 A+ 金标并排检查
- [x] 1280 响应式检查

此前状态：`IN_PROGRESS`。该记录对应 Sprint-003 之前的 Admin 基线验收；本次 Sprint-003 的最新验收见下方。

---

# Sprint-003 ServiceOS Admin 视觉验收

final result: passed

## 基准与实现

- source: `/var/folders/fy/7009cy9j35736hb68tvbccbc0000gn/T/codex-clipboard-167cafab-3715-43d6-92ce-69e1f8e4d327.png`
- comparison: `/Users/louis/code/serviceos/artifacts/sprint-003/qa-comparison.png`
- implementation contact sheet: `/Users/louis/code/serviceos/artifacts/sprint-003/qa-implementation-contact.png`
- browser: Codex In-app Browser，已登录本地 `Local Developer`，项目为“比亚迪山东家充项目”。
- viewport: `1920 × 1080` CSS px，`devicePixelRatio = 1`；截图采用 full-page capture，页面高度随内容增长。

交付截图像素尺寸：

| 页面 | 文件 | 像素 |
| --- | --- | --- |
| 项目列表 | `01-project-list.png` | 1920 × 1080 |
| 项目驾驶舱 | `02-project-cockpit.png` | 1920 × 1123 |
| 履约方案首页 | `03-blueprint-home.png` | 1920 × 1080 |
| 版本管理 | `04-version-management.png` | 1920 × 1080 |
| 流程设计器 | `05-flow-designer.png` | 1920 × 1361 |
| 任务模板 | `06-task-templates.png` | 1920 × 1080 |
| 表单设计 | `07-form-designer.png` | 1920 × 1230 |
| SLA 设计 | `08-sla-design.png` | 1920 × 1109 |

## 对照结论

### 全局对照

方案 A 的稳定专业、浅灰工作区、蓝色主行动、白色信息表面和高密度任务布局均得到保留。项目列表、驾驶舱和 Blueprint Designer 使用同一套 ServiceOS 令牌与组件，不再呈现默认 Vben CRUD 卡片堆叠。

### 局部对照

- 项目列表：有项目经理需要的四类运营指标、业务筛选、高密度字段列和“进入详情”入口。
- 项目驾驶舱：项目状态、客户/区域、履约方案版本、工单/SLA 指标、命名阶段进度、风险面板和业务时间线在同一工作区。
- Blueprint Designer：方案目录、设计面板、节点属性三栏结构；版本时间线、流程节点、任务模板、业务表单、证据规则与 SLA 规则均有独立设计面。
- 信息层级：技术 ID 不作为主要阅读对象；编辑器使用任务名称、负责人、目标时间、字段属性等业务语言。

## 发现与迭代记录

- 初始 P2：Vben 本地缓存使左侧导航残留折叠状态，且鼠标离开后自动收起。将 ServiceOS 侧栏配置为稳定展开并保留手动收起入口，全部截图重新生成。
- 初始 P2：驾驶舱阶段条曾只显示阶段数量。改为使用履约草稿中的阶段名称和当前阶段状态；缺少后端阶段名称时才使用明确的配置态文案。
- 初始 P2：表单说明文字包含技术术语 `JSON`，触发产品边界检查。改为“技术结构”，不放宽边界规则。
- 最终复核未发现 P0、P1 或 P2 视觉/交互问题。

## 浏览器交互走查

- 项目列表搜索“比亚迪”，确认列表收敛到目标项目；点击项目名称进入驾驶舱。
- 点击“进入方案设计器”，确认 Blueprint Designer 路由与三栏布局。
- 切换流程设计，选择“02 上门安装”，确认右侧节点属性切换。
- 切换任务模板，选择“阶段 02 上门安装任务”，确认任务属性切换。
- 切换表单设计，从字段库添加“选择”字段，确认字段属性面板仍可用。
- 切换 SLA 规则，选择“上门安装”表格行，确认目标/预警/升级属性切换。
- 切换版本管理，确认 V2/V1 时间线；点击“查看”显示版本选择状态，点击“复制生成新版本”进入活动草稿入口。
- 查询、筛选、编辑器字段添加和版本入口均未产生生产数据写入；编辑器内编辑态遵循现有草稿命令边界。

## 控制台与运行日志

- 浏览器 error：0。
- 浏览器 warning：仅有 Vben `StorageManager` 在开发环境中使用空前缀 LocalStorage 的既有提示；未发现本次页面组件、请求或渲染异常。
