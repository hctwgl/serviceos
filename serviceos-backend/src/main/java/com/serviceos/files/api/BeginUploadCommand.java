package com.serviceos.files.api;

/**
 * 创建上传会话。checksum 必须是客户端对原始字节计算的 SHA-256；服务端 finalize 时重新计算。
 */
public record BeginUploadCommand(
        String businessContextType,
        String businessContextId,
        String originalFileName,
        String declaredMimeType,
        long expectedSize,
        String expectedSha256
) {
}
