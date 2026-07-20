package com.serviceos.configuration.api;

/**
 * 编译预览响应：产品化 Runbook + 诊断用 Manifest JSON。
 *
 * <p>普通产品 UI 只应渲染 {@link #runbook()}；{@link #manifestJson()} 仅供诊断抽屉。</p>
 */
public record ProjectFulfillmentManifestView(
        String manifestJson,
        String contentDigest,
        ProjectFulfillmentRunbook runbook
) {
}
