package com.serviceos.integration.geely.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeelyAesCipherTest {
    private static final String KEY = "GENERAL_KEY_DEMO";
    private static final String IV = "IV_DEMO_90123456";
    private static final String PLAIN =
            "{\"installProcessNo\":\"IN2025081415311100001\",\"province\":\"110000\",\"city\":\"110100\",\"district\":\"110105\",\"carryProduct\":1}";
    /** 协议 4.5.2 示例密文（去除换行）。 */
    private static final String DOC_CIPHER =
            "5/kTRk7sC1KtNyMpI4bDMnZDGI9r0Qo+Huqs3f1iqbxx2QozuL/6wpk9PS9mQaqre3T/lPHdHR3qg6"
                    + "KOTBaYDa27KUN3+02SGaN0BEk/3ozQ+q1CecbRDR5CcvWVQAgV8BKe+IoyVHYTLLYfr45VL3f6Yd9j"
                    + "gl6U0VGwIoQ2z0g=";

    @Test
    void roundTripsLocalPlaintext() {
        String cipher = GeelyAesCipher.encryptToBase64(PLAIN, KEY, IV);
        assertThat(GeelyAesCipher.decryptFromBase64(cipher, KEY, IV)).isEqualTo(PLAIN);
    }

    @Test
    void decryptsProtocolDocumentSampleCiphertext() {
        String plain = GeelyAesCipher.decryptFromBase64(DOC_CIPHER, KEY, IV);
        assertThat(plain).contains("IN2025081415311100001");
        assertThat(plain).contains("\"province\":\"110000\"");
    }
}
