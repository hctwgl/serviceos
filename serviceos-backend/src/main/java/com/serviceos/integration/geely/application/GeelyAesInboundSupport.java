package com.serviceos.integration.geely.application;

import com.serviceos.integration.geely.api.GeelyNotifyEnvelope;
import com.serviceos.integration.geely.infrastructure.GeelyAesCipher;
import tools.jackson.databind.ObjectMapper;

/**
 * 吉利出向通知外壳 AES 解密共用步骤。
 */
final class GeelyAesInboundSupport {
    private GeelyAesInboundSupport() {
    }

    record DecryptedPayload(GeelyNotifyEnvelope envelope, String plainJson, byte[] plainBytes) {
    }

    static DecryptedPayload decrypt(
            ObjectMapper objectMapper,
            byte[] rawEnvelope,
            String accessKey,
            String aesIv
    ) {
        GeelyNotifyEnvelope envelope = objectMapper.readValue(rawEnvelope, GeelyNotifyEnvelope.class);
        if (envelope.data() == null || envelope.data().isBlank()) {
            throw new IllegalArgumentException("missing encrypted data");
        }
        String plainJson = GeelyAesCipher.decryptFromBase64(envelope.data(), accessKey, aesIv);
        byte[] plainBytes = plainJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new DecryptedPayload(envelope, plainJson, plainBytes);
    }
}
