---
title: M264 Technician 在线资料上传交付批次验收矩阵
status: Implemented
milestone: M264
lastUpdated: 2026-07-18
---

# M264 Technician 在线资料上传交付批次验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M264-01 | Technician 命令边界 | Begin 不接受 tenant/uploader/offline/locationVerified/onBehalf/object key | OpenAPI + MVC + clients |
| M264-02 | Context 与责任 | ACTIVE Profile/关系、资源网点和当前责任均实时成立 | service unit + MVC |
| M264-03 | Evidence 授权 | 内核重新验证 read/submit/upload capability 与 RUNNING/guard | unit + Evidence IT |
| M264-04 | Slot/item 安全摘要 | 不暴露文件对象、上传人、原始采集元数据或永久 URL | contract + E2E |
| M264-05 | Begin | 文件名、MIME、size、SHA-256、来源与采集时间受控 | unit + clients |
| M264-06 | 数据面 PUT | 只携带短期 URL/requiredHeaders，不携带 Token/Context/client metadata | Playwright + Swift smoke |
| M264-07 | Finalize | actualSha256 与 command ID 明确，重复命令由内核收敛 | Evidence PostgreSQL IT |
| M264-08 | 数量与 revision | 未达 maxCount 创建 item，达到后显式追加 revision | H5/iOS source + Evidence IT |
| M264-09 | 状态诚实 | STORED/VALIDATING 不显示为扫描或校验完成 | H5/iOS E2E/source |
| M264-10 | iOS 采集 | 相机、相册和文件选择均只进入前台内存上传 | App build/XCTest/XCUITest |
| M264-11 | iOS 隐私 | Camera usage、Photos/Videos manifest、Production archive/dSYM 正确 | distribution gate |
| M264-12 | H5 | build 与 12 条 Playwright 通过 | `technician-web` |
| M264-13 | 契约客户端 | OpenAPI 1.0.24 兼容并生成 TS/Swift 客户端 | contract/client gates |
| M264-14 | 模块/全量 | Evidence 公开 API 依赖合法且最终 L3 通过 | ArchitectureTest + `verify-local.sh` |

## 明确未验收

物理设备采集、弱网重试、断点续传、后台/离线上传、生产对象存储/扫描、大文件压力、EvidenceSetSnapshot、
真实 operationRefs 签退、整改、签名真机、真实 IdP、VoiceOver 和 TestFlight 仍未验收。
