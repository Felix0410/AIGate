package com.felix.aigate.application.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * API Key secret 的哈希工具：创建与认证共用同一套算法，保证规则统一。
 *
 * <p>算法固定为 UTF-8 -> SHA-256 -> lowercase hex，与 P2-T04 落库格式完全一致。
 * 比对使用 {@link MessageDigest#isEqual} 常量时间比较，避免时序侧信道。
 */
@Component
public class ApplicationApiKeyHasher {

    public String hash(String secret) {
        return HexFormat.of().formatHex(sha256(secret));
    }

    public boolean matches(String secret, String storedHash) {
        byte[] supplied = sha256(secret);
        byte[] expected = HexFormat.of().parseHex(storedHash);
        return MessageDigest.isEqual(supplied, expected);
    }

    private byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to hash API key", exception);
        }
    }
}
