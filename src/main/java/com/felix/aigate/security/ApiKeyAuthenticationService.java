package com.felix.aigate.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.felix.aigate.application.entity.Application;
import com.felix.aigate.application.entity.ApplicationApiKey;
import com.felix.aigate.application.entity.ApplicationApiKeyStatus;
import com.felix.aigate.application.mapper.ApplicationApiKeyMapper;
import com.felix.aigate.application.mapper.ApplicationMapper;
import com.felix.aigate.application.service.ApplicationApiKeyHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

/**
 * 校验原始 API Key 并还原成 {@link ApplicationIdentity}。
 *
 * <p>所有失败（格式错误 / keyId 不存在 / 非 ACTIVE / secret 不匹配 / Application 不存在）
 * 统一抛出 {@link BadCredentialsException}，不区分细节，避免泄露 keyId 是否存在。
 */
@Service
@RequiredArgsConstructor
public class ApiKeyAuthenticationService {

    private static final String KEY_PREFIX = "aig_live_";
    private static final char SEPARATOR = '.';

    private final ApplicationApiKeyMapper applicationApiKeyMapper;
    private final ApplicationMapper applicationMapper;
    private final ApplicationApiKeyHasher applicationApiKeyHasher;

    public ApplicationIdentity authenticate(String rawApiKey) {

        ParsedKey parsed = parse(rawApiKey);

        ApplicationApiKey apiKey = applicationApiKeyMapper.selectOne(
                new LambdaQueryWrapper<ApplicationApiKey>()
                        .eq(ApplicationApiKey::getKeyId, parsed.keyId()));

        if (apiKey == null
                || apiKey.getStatus() != ApplicationApiKeyStatus.ACTIVE
                || !applicationApiKeyHasher.matches(parsed.secret(), apiKey.getKeyHash())) {
            throw invalid();
        }

        Application application = applicationMapper.selectById(apiKey.getApplicationId());
        if (application == null) {
            throw invalid();
        }

        return new ApplicationIdentity(
                application.getId(),
                application.getName(),
                application.getTeamId()
        );
    }

    private ParsedKey parse(String rawApiKey) {

        if (rawApiKey == null || !rawApiKey.startsWith(KEY_PREFIX)) {
            throw invalid();
        }

        String body = rawApiKey.substring(KEY_PREFIX.length());
        int separatorIndex = body.indexOf(SEPARATOR);

        // keyId 与 secret 都必须非空，且只能被唯一正确拆分（secret 内不允许再出现分隔符）。
        if (separatorIndex <= 0 || separatorIndex == body.length() - 1
                || body.indexOf(SEPARATOR, separatorIndex + 1) >= 0) {
            throw invalid();
        }

        String keyId = body.substring(0, separatorIndex);
        String secret = body.substring(separatorIndex + 1);
        return new ParsedKey(keyId, secret);
    }

    private BadCredentialsException invalid() {
        // 固定文案：绝不携带 rawApiKey / secret / keyHash / Authorization 等敏感内容。
        return new BadCredentialsException("Invalid API key");
    }

    private record ParsedKey(String keyId, String secret) {
    }
}
