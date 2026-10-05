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

        String tampered =
                encrypted.substring(0, encrypted.length() - 2)
                        + "AA";

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
    @DisplayName("Credential 为 null 时保持 null")
    void nullCredentialShouldRemainNull() {

        CredentialService credentialService =
                new CredentialService(createMasterKey());

        assertNull(credentialService.encrypt(null));
        assertNull(credentialService.decrypt(null));
    }

    private String createMasterKey() {
        byte[] key = new byte[32];

        new SecureRandom().nextBytes(key);

        return Base64.getEncoder()
                .encodeToString(key);
    }
}