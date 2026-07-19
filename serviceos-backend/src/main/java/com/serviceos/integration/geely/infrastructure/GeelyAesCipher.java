package com.serviceos.integration.geely.infrastructure;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 吉利浩瀚家充协议 AES-128-CBC/PKCS5Padding。
 *
 * <p>密钥取 AccessKey 前 16 字节；IV 由浩瀚邮件分配。本类仅用于本地契约测试与入站解密，
 * 不得将生产 Secret 写入仓库。</p>
 */
public final class GeelyAesCipher {
    private GeelyAesCipher() {
    }

    public static String encryptToBase64(String plainText, String accessKey, String iv) {
        byte[] encrypted = transform(Cipher.ENCRYPT_MODE, plainText.getBytes(StandardCharsets.UTF_8),
                accessKey, iv);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String decryptFromBase64(String cipherBase64, String accessKey, String iv) {
        String normalized = cipherBase64.replaceAll("\\s+", "");
        byte[] encrypted = Base64.getDecoder().decode(normalized);
        byte[] plain = transform(Cipher.DECRYPT_MODE, encrypted, accessKey, iv);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private static byte[] transform(int mode, byte[] input, String accessKey, String iv) {
        byte[] keyBytes = key16(accessKey);
        byte[] ivBytes = iv16(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(mode, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(ivBytes));
            return cipher.doFinal(input);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Geely AES transform failed: " + exception.getMessage(),
                    exception);
        }
    }

    private static byte[] key16(String accessKey) {
        if (accessKey == null || accessKey.isBlank()) {
            throw new IllegalArgumentException("accessKey must not be blank");
        }
        String key = accessKey.length() <= 16 ? accessKey : accessKey.substring(0, 16);
        if (key.length() != 16) {
            throw new IllegalArgumentException("Geely AES key must be 16 bytes after truncation");
        }
        return key.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] iv16(String iv) {
        if (iv == null || iv.getBytes(StandardCharsets.UTF_8).length != 16) {
            throw new IllegalArgumentException("Geely AES IV must be exactly 16 bytes");
        }
        return iv.getBytes(StandardCharsets.UTF_8);
    }
}
