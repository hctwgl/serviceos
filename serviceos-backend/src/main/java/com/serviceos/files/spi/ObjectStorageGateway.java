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

    ObjectTransferAuthorization authorizeDownload(
            String objectKey,
            String responseMimeType,
            Duration lifetime
    );
}
