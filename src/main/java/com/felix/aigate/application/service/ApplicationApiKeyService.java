package com.felix.aigate.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.felix.aigate.application.entity.Application;
import com.felix.aigate.application.entity.ApplicationApiKey;
import com.felix.aigate.application.entity.ApplicationApiKeyStatus;
import com.felix.aigate.application.mapper.ApplicationApiKeyMapper;
import com.felix.aigate.application.mapper.ApplicationMapper;
import com.felix.aigate.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationApiKeyService {

    private static final String KEY_PREFIX = "aig_live_";
    private static final int KEY_ID_BYTES = 16;
    private static final int SECRET_BYTES = 32;

    private final ApplicationApiKeyMapper applicationApiKeyMapper;
    private final ApplicationMapper applicationMapper;

    private final SecureRandom secureRandom = new SecureRandom();

    public ApplicationApiKeyCreationResult createApiKey(Long applicationId, String name) {

        ensureApplicationExists(applicationId);

        String keyId = randomUrlSafe(KEY_ID_BYTES);
        String secret = randomUrlSafe(SECRET_BYTES);
        String apiKey = KEY_PREFIX + keyId + "." + secret;
        String keyHash = sha256Hex(secret);

        ApplicationApiKey apiKeyEntity = new ApplicationApiKey();
        apiKeyEntity.setApplicationId(applicationId);
        apiKeyEntity.setName(name);
        apiKeyEntity.setKeyId(keyId);
        apiKeyEntity.setKeyPrefix(KEY_PREFIX + keyId);
        apiKeyEntity.setKeyHash(keyHash);
        apiKeyEntity.setStatus(ApplicationApiKeyStatus.ACTIVE);
        apiKeyEntity.setRevokedAt(null);

        applicationApiKeyMapper.insert(apiKeyEntity);

        ApplicationApiKey persisted = applicationApiKeyMapper.selectById(apiKeyEntity.getId());

        return new ApplicationApiKeyCreationResult(persisted, apiKey);
    }

    public List<ApplicationApiKey> listApiKeys(Long applicationId) {

        ensureApplicationExists(applicationId);

        LambdaQueryWrapper<ApplicationApiKey> wrapper =
                new LambdaQueryWrapper<ApplicationApiKey>()
                        .eq(ApplicationApiKey::getApplicationId, applicationId);

        return applicationApiKeyMapper.selectList(wrapper);
    }

    public ApplicationApiKey revokeApiKey(Long applicationId, Long apiKeyId) {

        ensureApplicationExists(applicationId);

        ApplicationApiKey apiKey = findApiKey(applicationId, apiKeyId);

        // 已 REVOKED：直接返回当前记录，不重写 revokedAt，保证幂等。
        if (apiKey.getStatus() == ApplicationApiKeyStatus.REVOKED) {
            return apiKey;
        }

        apiKey.setStatus(ApplicationApiKeyStatus.REVOKED);
        apiKey.setRevokedAt(Instant.now());

        applicationApiKeyMapper.updateById(apiKey);

        return applicationApiKeyMapper.selectById(apiKeyId);
    }

    private ApplicationApiKey findApiKey(Long applicationId, Long apiKeyId) {

        LambdaQueryWrapper<ApplicationApiKey> wrapper =
                new LambdaQueryWrapper<ApplicationApiKey>()
                        .eq(ApplicationApiKey::getId, apiKeyId)
                        .eq(ApplicationApiKey::getApplicationId, applicationId);

        ApplicationApiKey apiKey = applicationApiKeyMapper.selectOne(wrapper);
        if (apiKey == null) {
            throw new ResourceNotFoundException("API_KEY_NOT_FOUND", "API key not found");
        }
        return apiKey;
    }

    private void ensureApplicationExists(Long applicationId) {
        Application application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new ResourceNotFoundException("APPLICATION_NOT_FOUND", "Application not found");
        }
    }

    private String randomUrlSafe(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to hash API key", exception);
        }
    }
}
