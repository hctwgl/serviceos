package com.serviceos.files.infrastructure;

import java.io.IOException;
import java.io.InputStream;

/** 仅供本地私有存储传输端点使用；生产 S3 适配器直接返回对象存储预签名 URL。 */
interface LocalObjectTransferService {
    void upload(String token, String contentType, long contentLength, InputStream content) throws IOException;

    DownloadedObject download(String token) throws IOException;

    record DownloadedObject(InputStream content, long size, String mimeType) {
    }
}
