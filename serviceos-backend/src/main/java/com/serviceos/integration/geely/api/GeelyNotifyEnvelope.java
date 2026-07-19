package com.serviceos.integration.geely.api;

/**
 * 吉利浩瀚出向通知外壳：providerNo + timestamp + AES(data)。
 *
 * <p>OpenAPI/开放平台统一签名仍为 TBD_EXTERNAL_CONTRACT；本地联调仅校验 AES 解密。</p>
 */
public record GeelyNotifyEnvelope(
        String providerNo,
        String timestamp,
        String data
) {
}
