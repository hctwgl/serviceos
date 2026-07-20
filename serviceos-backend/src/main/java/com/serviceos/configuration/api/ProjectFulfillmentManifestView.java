package com.serviceos.configuration.api;

/** 服务端编译的运行 Manifest（JSON 字符串，前端不得再拼装）。 */
public record ProjectFulfillmentManifestView(
        String manifestJson,
        String contentDigest
) {
}
