# 比亚迪海洋山东试点报文示例

本目录中的 JSON 仅用于契约测试和桌面演练，不包含真实用户数据，不得直接作为生产报文。

## 文件

- `survey-info.json`：勘测信息回传示例；
- `installation-info.json`：安装信息回传示例；
- `accessories-info.json`：安装附件回传示例；
- `review-callback.json`：厂端审核驳回回调示例。

## 重要说明

`accessories-info.json` 中的 URL 均为测试地址。`increaseChargeImage` 当前使用测试占位地址，仅为了满足接口文档标记的必传要求；在“无增项”的正式口径得到比亚迪确认前，该示例不得作为生产规则依据，也不得上传伪造图片。

所有生产附件必须来自 ServiceOS 文件安全链路，并满足扫描、授权、有效期、可访问性和审计要求。
