# ServiceOS 服务覆盖批准视觉参考

本目录保存产品负责人于 2026-07-21 批准的 ServiceOS Admin“服务覆盖管理”视觉参考图。

这些图片不是可忽略的灵感素材，而是页面布局、信息层级、地图与业务数据比例、配置流程和审阅方式的**强制视觉参考**。

实现任务必须同时读取：

- `serviceos-architecture/product/admin/12-classic-professional-visual-baseline.md`；
- `serviceos-architecture/product/admin/13-service-coverage-amap-visual-baseline.md`；
- 本目录四张参考图。

图片由已批准的原始概念图无内容改绘地压缩为 WebP，仅用于降低仓库体积。示例名称、日期和数量不是生产数据。

## 文件清单

| 文件 | 页面 | 用途 |
|---|---|---|
| `01-project-service-coverage-overview.webp` | 项目服务覆盖总览 | 全国地图、覆盖摘要、省份明细和风险列表 |
| `02-network-service-region-configuration.webp` | 网点服务区域配置 | 省市树、地图联动、已选区域、影响与校验 |
| `03-national-service-coverage-analysis.webp` | 全国服务覆盖分析 | 覆盖率、城市覆盖、重叠与风险分析 |
| `04-service-region-change-review.webp` | 服务区域变更审阅 | 变更前后、影响分析、版本记录和确认生效 |

## 完整性校验

- `01-project-service-coverage-overview.webp`：项目服务覆盖总览，120056 bytes，SHA-256 `1e0822b8beff05d1353c28ab3e02ac9a2fb868a522916b34f6bc21dd0a94cd2d`
- `02-network-service-region-configuration.webp`：网点服务区域配置，117924 bytes，SHA-256 `789e911c6529cc8dc3ee83baa58201a396330c2bcaa984b3c849aaad602bb9d3`
- `03-national-service-coverage-analysis.webp`：全国服务覆盖分析，123968 bytes，SHA-256 `6a4d57a5afd504bacfaba7c36e886185e8afc5e699e4783fc3b1c2a8f2da4bf0`
- `04-service-region-change-review.webp`：服务区域变更审阅，132252 bytes，SHA-256 `65be1afb61cacdb04dc6ae1ee879b6781b547b3c7109cd7f3d0d79e75cb54241`

## 实施证据要求

相关实现 PR 必须提供：

1. 参考图与真实页面的并排截图；
2. 1440×1024 和 1280px 宽度证据；
3. 页面结构差异说明；
4. 正常、空、错误、无权限、只读、冲突和提交中状态；
5. 未经产品负责人批准，不得以“技术实现方便”为理由改变核心布局。
