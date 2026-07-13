package com.serviceos.files.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 小而确定的魔数识别器。它只承担 finalize 的“声明类型不能伪造”基线，生产环境仍需专业格式解析器。
 */
final class FileMagic {
    private FileMagic() {
    }

    static String detect(InputStream content) throws IOException {
        byte[] prefix = content.readNBytes(512);
        if (startsWith(prefix, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return "image/jpeg";
        }
        if (startsWith(prefix, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return "image/png";
        }
        if (startsWith(prefix, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                || startsWith(prefix, "GIF89a".getBytes(StandardCharsets.US_ASCII))) {
            return "image/gif";
        }
        if (startsWith(prefix, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return "application/pdf";
        }
        if (prefix.length >= 12
                && prefix[4] == 'f' && prefix[5] == 't' && prefix[6] == 'y' && prefix[7] == 'p') {
            return "video/mp4";
        }
        if (startsWith(prefix, new byte[]{0x50, 0x4B, 0x03, 0x04})) {
            return "application/zip";
        }
        if (looksLikeUtf8Text(prefix)) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        return value.length >= prefix.length
                && Arrays.equals(Arrays.copyOf(value, prefix.length), prefix);
    }

    private static boolean looksLikeUtf8Text(byte[] value) {
        if (value.length == 0) {
            return false;
        }
        for (byte item : value) {
            int unsigned = item & 0xFF;
            if (unsigned == 0) {
                return false;
            }
            if (unsigned < 0x20 && unsigned != '\n' && unsigned != '\r' && unsigned != '\t') {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(value));
            return true;
        } catch (java.nio.charset.CharacterCodingException exception) {
            return false;
        }
    }
}
