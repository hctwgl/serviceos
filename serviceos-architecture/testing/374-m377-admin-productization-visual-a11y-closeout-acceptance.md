---
title: M377 Admin 产品化视觉可访问性回归关闭验收
status: Accepted
milestone: M377
lastUpdated: 2026-07-20
---

| ID | 期望 | 证据 |
|---|---|---|
| V1 | 工单中心产品化截图无完整 UUID | admin-productization-visual.spec.ts |
| V2 | AppShell/ScopeBar 可见 | 同上 |
| V3 | Empty/Error/详情/项目/审核/用户/角色/工作台截图 | `__screenshots__/admin-*.png` |
| V4 | axe AA 扫描关键页通过（排除 Ant Select 内部） | `npm run test:a11y` |
| V5 | mock 产品化冒烟通过 | admin-productization-smoke.spec.ts |
| V6 | check:tokens / unit / build 通过 | npm scripts |
| V7 | 06 status 仍为 Proposed | product/06 frontmatter |
| V8 | OIDC 冒烟 | **BLOCKED**：Docker 不可用 |

## 人工检查清单（已按实现走查）

- [x] 焦点 outline 全局样式存在
- [x] prefers-reduced-motion Token/CSS 存在
- [x] SLA aria-live 仅阶段变化播报（SlaCountdown）
- [x] 键盘：全局搜索 / 侧栏折叠（Playwright）
- [ ] 200% 缩放真机人工（本环境未做桌面缩放录屏）
- [ ] 完整读屏旅程人工
- [ ] 真实 OIDC 冒烟（Docker 不可用）