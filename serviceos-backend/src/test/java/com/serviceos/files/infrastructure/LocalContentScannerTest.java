package com.serviceos.files.infrastructure;

import com.serviceos.files.spi.ScanOutcome;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LocalContentScannerTest {
    private final LocalContentScanner scanner = new LocalContentScanner();

    @Test
    void cleanContentPassesAndEicarSignatureIsQuarantined() throws Exception {
        ScanOutcome clean = scanner.scan(
                new ByteArrayInputStream("normal field photo".getBytes(StandardCharsets.UTF_8)),
                18, "text/plain");
        ScanOutcome malicious = scanner.scan(
                new ByteArrayInputStream(
                        "prefix-EICAR-STANDARD-ANTIVIRUS-TEST-FILE-suffix"
                                .getBytes(StandardCharsets.US_ASCII)),
                50, "text/plain");

        assertThat(clean.result()).isEqualTo(ScanOutcome.Result.CLEAN);
        assertThat(malicious.result()).isEqualTo(ScanOutcome.Result.MALICIOUS);
        assertThat(malicious.reasonCode()).isEqualTo("EICAR_TEST_SIGNATURE");
    }
}
