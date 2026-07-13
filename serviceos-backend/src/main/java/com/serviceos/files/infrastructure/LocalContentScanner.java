package com.serviceos.files.infrastructure;

import com.serviceos.files.spi.FileContentScanner;
import com.serviceos.files.spi.ScanOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 本地可执行扫描沙箱：识别业界标准 EICAR 测试特征。
 * 生产环境必须替换为受管病毒/内容安全服务，不能把本实现宣称为完整反病毒能力。
 */
@Component
@ConditionalOnProperty(
        name = "serviceos.files.scanner",
        havingValue = "local-eicar",
        matchIfMissing = true
)
final class LocalContentScanner implements FileContentScanner {
    private static final byte[] EICAR = "EICAR-STANDARD-ANTIVIRUS-TEST-FILE"
            .getBytes(StandardCharsets.US_ASCII);
    private static final int[] PREFIX = prefixTable(EICAR);

    @Override
    public ScanOutcome scan(InputStream content, long size, String detectedMimeType) throws Exception {
        int matched = 0;
        int value;
        while ((value = content.read()) >= 0) {
            byte current = (byte) value;
            while (matched > 0 && EICAR[matched] != current) {
                matched = PREFIX[matched - 1];
            }
            if (EICAR[matched] == current) {
                matched++;
                if (matched == EICAR.length) {
                    return ScanOutcome.malicious("local-eicar", "1", "EICAR_TEST_SIGNATURE");
                }
            }
        }
        return ScanOutcome.clean("local-eicar", "1");
    }

    private static int[] prefixTable(byte[] pattern) {
        int[] prefix = new int[pattern.length];
        int matched = 0;
        for (int index = 1; index < pattern.length; index++) {
            while (matched > 0 && pattern[matched] != pattern[index]) {
                matched = prefix[matched - 1];
            }
            if (pattern[matched] == pattern[index]) {
                matched++;
            }
            prefix[index] = matched;
        }
        return prefix;
    }
}
