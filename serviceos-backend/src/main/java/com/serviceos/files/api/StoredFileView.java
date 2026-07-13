package com.serviceos.files.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 文件引用不包含 objectKey。对象路径只属于 files 模块，防止绕过授权直接访问存储。
 */
public record StoredFileView(
        UUID fileId,
        String tenantId,
        String originalFileName,
        String checksumSha256,
        long size,
        String declaredMimeType,
        String detectedMimeType,
        String lifecycleStatus,
        String quarantineReason,
        Instant createdAt,
        long version
) {
}
