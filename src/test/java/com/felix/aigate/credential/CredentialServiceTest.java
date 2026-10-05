package com.felix.aigate.credential;

import com.felix.aigate.credential.service.CredentialService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CredentialServiceTest {

    @Test
    @DisplayName("Credential 加密后可以恢复原文")
    void encryptAndDecryptShouldRestoreCredential() {

        CredentialService credentialService =
                new CredentialService(createMasterKey());

        String credential = "sk-test-secret";

        String encrypted = credentialService.encrypt(credential);

        String decrypted =
                credentialService.decrypt(encrypted);

        assertNotEquals(credential, encrypted);
        assertEquals(credential, decrypted);
    }

    @Test
    @DisplayName("相同 Credential 两次加密应产生不同密文")
    void sameCredentialShouldProduceDifferentCiphertext() {

        CredentialService credentialService =
                new CredentialService(createMasterKey());

        String credential = "sk-test-secret";

        String encrypted1 =
                credentialService.encrypt(credential);

        String encrypted2 =
                credentialService.encrypt(credential);

        assertNotEquals(encrypted1, encrypted2);
    }

    @Test
    @DisplayName("密文被篡改后解密应失败")
    void tamperedCredentialShouldFailToDecrypt() {

        CredentialService credentialService =
                new CredentialService(createMasterKey());

        String encrypted =
                credentialService.encrypt("sk-test-secret");

        // 确定性篡改：拆信封后固定翻转密文首字节，保证密文一定发生变化
        String tampered = tamperCiphertext(encrypted);

        assertThrows(
                IllegalStateException.class,
                () -> credentialService.decrypt(tampered)
        );
    }

    @Test
    @DisplayName("Master Key 长度错误时创建 CredentialService 应失败")
    void invalidMasterKeyLengthShouldFail() {

        String invalidKey = Base64.getEncoder()
                .encodeToString(new byte[16]);

        assertThrows(
                IllegalStateException.class,
                () -> new CredentialService(invalidKey)
        );
    }

    @Test
    @DisplayName("使用错误 Master Key 解密应失败")
    void decryptWithWrongMasterKeyShouldFail() {

        CredentialService encryptService =
                new CredentialService(createMasterKey());

        String encrypted =
                encryptService.encrypt("sk-test-secret");

        CredentialService wrongService =
                new CredentialService(createMasterKey());

        assertThrows(
                IllegalStateException.class,
                () -> wrongService.decrypt(encrypted)
        );
    }

    @Test
    @DisplayName("Credential 为 null 时保持 null")
    void nullCredentialShouldRemainNull() {

        CredentialService credentialService =
                new CredentialService(createMasterKey());

        assertNull(credentialService.encrypt(null));
        assertNull(credentialService.decrypt(null));
    }

    /** 拆信封 -> 解码密文段 -> 固定翻转首字节 -> 重新编码回信封，确保确定性篡改。 */
    private String tamperCiphertext(String encrypted) {
        String[] parts = encrypted.split(":", 3);

        byte[] cipherText = Base64.getDecoder().decode(parts[2]);
        cipherText[0] ^= 0x01;

        parts[2] = Base64.getEncoder().encodeToString(cipherText);

        return parts[0] + ":" + parts[1] + ":" + parts[2];
    }

    private String createMasterKey() {
        byte[] key = new byte[32];

        new SecureRandom().nextBytes(key);

        return Base64.getEncoder()
                .encodeToString(key);
    }
}