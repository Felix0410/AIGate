package com.felix.aigate.credential.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CredentialService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String VERSION = "v1";

    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec secretKey;

    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialService(@Value("${AIGATE_MASTER_KEY}") String masterKey) {
        this.secretKey = createSecretKey(masterKey);
    }

    public String encrypt(String credential) {
        if (credential == null) {
            return null;
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKey,
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv)
            );

            byte[] encrypted = cipher.doFinal(
                    credential.getBytes(StandardCharsets.UTF_8)
            );

            return VERSION
                    + ":"
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(encrypted);

        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Failed to encrypt credential",
                    exception
            );
        }
    }

    public String decrypt(String encryptedCredential) {
        if (encryptedCredential == null) {
            return null;
        }

        try {
            String[] parts = encryptedCredential.split(":", 3);

            if (parts.length != 3 || !VERSION.equals(parts[0])) {
                throw new IllegalStateException(
                        "Invalid encrypted credential format"
                );
            }

            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getDecoder().decode(parts[2]);

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv)
            );

            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(
                    decrypted,
                    StandardCharsets.UTF_8
            );

        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Failed to decrypt credential",
                    exception
            );
        }
    }

    private SecretKeySpec createSecretKey(String masterKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(masterKey);

            if (keyBytes.length != KEY_LENGTH_BYTES) {
                throw new IllegalStateException(
                        "AIGATE_MASTER_KEY must decode to 32 bytes"
                );
            }

            return new SecretKeySpec(
                    keyBytes,
                    "AES"
            );

        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "AIGATE_MASTER_KEY must be valid Base64",
                    exception
            );
        }
    }
}