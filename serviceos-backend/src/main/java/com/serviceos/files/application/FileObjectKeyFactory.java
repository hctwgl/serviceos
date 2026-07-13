package com.serviceos.files.application;

import com.serviceos.shared.Sha256;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 对象 key 完全由服务端生成，不包含原文件名、用户地址或可注入路径片段。
 */
final class FileObjectKeyFactory {
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter
            .ofPattern("uuuu/MM/dd")
            .withZone(ZoneOffset.UTC);

    private FileObjectKeyFactory() {
    }

    static String create(String tenantId, UUID sessionId, Instant now) {
        String tenantPartition = Sha256.digest(tenantId).substring(0, 16);
        return "pending/" + tenantPartition + "/" + DATE_PATH.format(now) + "/" + sessionId;
    }
}
