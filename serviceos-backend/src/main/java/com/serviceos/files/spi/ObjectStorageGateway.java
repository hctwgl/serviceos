package com.serviceos.files.spi;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/**
 * 私有对象存储端口。生产适配器应返回受 objectKey、大小、MIME 和有效期约束的预签名 URL。
 */
public interface ObjectStorageGateway {
    ObjectTransferAuthorization authorizeUpload(
            String objectKey,
            long exactSize,
            String declaredMimeType,
            Duration lifetime
    );

    ObjectMetadata inspect(String objectKey) throws IOException;

    InputStream openForScan(String objectKey) throws IOException;

    /**
     * 服务端把已经完成协议校验的不可变内容写入私有对象存储。
     *
     * <p>该入口不签发客户端上传凭证；实现必须原子写入，并在同一 objectKey 已存在时只接受
     * 完全相同的大小与摘要。它用于入站原文、生成报告等服务端事实，不能作为覆盖对象的通道。</p>
     */
    ObjectMetadata storeInternal(
            String objectKey,
            InputStream content,
            long exactSize,
            String checksumSha256,
            String contentType
    ) throws IOException;

    ObjectTransferAuthorization authorizeDownload(
            String objectKey,
            String responseMimeType,
            Duration lifetime
    );
}
