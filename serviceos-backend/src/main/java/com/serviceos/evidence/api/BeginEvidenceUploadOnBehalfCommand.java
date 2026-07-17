package com.serviceos.evidence.api;

import java.util.UUID;

/**
 * Network Portal / 受控代补 Begin Evidence Upload 命令。
 * <p>
 * {@code onBehalfOf}/{@code onBehalfReason} 为命令级权威字段；不得放入客户端 captureMetadata。
 * {@code networkId} 可选，供 NETWORK scope 鉴权。
 */
public record BeginEvidenceUploadOnBehalfCommand(
        UUID taskId,
        UUID slotId,
        UUID evidenceItemId,
        String originalFileName,
        String declaredMimeType,
        long expectedSize,
        String expectedSha256,
        String captureMetadataJson,
        String onBehalfOf,
        String onBehalfReason,
        UUID networkId
) {
}
