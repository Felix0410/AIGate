package com.felix.aigate.application;

import com.felix.aigate.application.service.ApplicationApiKeyHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hasher 单元测试：hash 稳定、64 位 lowercase hex，matches 对正确/错误 secret 的判定。
 */
class ApplicationApiKeyHasherTest {

    private final ApplicationApiKeyHasher hasher = new ApplicationApiKeyHasher();

    @Test
    @DisplayName("同一 secret 的 hash 稳定，且为 64 位 lowercase hex")
    void hashShouldBeStableLowercaseHex() {

        String secret = "a-256-bit-secret-value";

        String first = hasher.hash(secret);
        String second = hasher.hash(secret);

        assertEquals(first, second);
        assertEquals(64, first.length());
        assertTrue(first.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("matches 正确 secret -> true，错误 secret -> false")
    void matchesShouldVerifySecret() {

        String secret = "correct-secret";
        String hash = hasher.hash(secret);

        assertTrue(hasher.matches(secret, hash));
        assertFalse(hasher.matches("wrong-secret", hash));
    }
}
