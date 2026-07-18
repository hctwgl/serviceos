package com.serviceos.integration.referenceoem.infrastructure;

import com.serviceos.shared.Sha256;

/**
 * REFERENCE / SAMPLE 验签。
 *
 * <p>签名原文：{@code appSecret|nonce|rawPayloadSha256}。仅用于演示，不得当作真实车企协议
 * （{@code TBD_EXTERNAL_CONTRACT}）。</p>
 */
public final class ReferenceOemSampleSignature {
    private ReferenceOemSampleSignature() {
    }

    public static String sign(String appSecret, String nonce, byte[] rawPayload) {
        return Sha256.digest(appSecret + "|" + nonce + "|" + Sha256.digest(rawPayload));
    }

    public static boolean matches(String appSecret, String nonce, byte[] rawPayload, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        return sign(appSecret, nonce, rawPayload).equalsIgnoreCase(signature.trim());
    }
}
