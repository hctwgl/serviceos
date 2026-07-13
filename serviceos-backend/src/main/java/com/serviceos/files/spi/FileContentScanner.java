package com.serviceos.files.spi;

import java.io.InputStream;

/**
 * 内容安全扫描端口。扫描器必须返回自身和规则版本，保证历史结果可解释。
 */
public interface FileContentScanner {
    ScanOutcome scan(InputStream content, long size, String detectedMimeType) throws Exception;
}
