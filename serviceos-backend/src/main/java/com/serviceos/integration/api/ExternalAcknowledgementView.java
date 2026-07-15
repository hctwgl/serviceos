package com.serviceos.integration.api;

import java.time.Instant;
import java.util.UUID;

/** 外部业务确认摘要；原始响应仅保存在 integration 私有对象存储。 */
public record ExternalAcknowledgementView(
        UUID acknowledgementId,
        String acknowledgementType,
        String result,
        String reasonCode,
        String responseDigest,
        String mappingVersionId,
        Instant receivedAt
) {
}
