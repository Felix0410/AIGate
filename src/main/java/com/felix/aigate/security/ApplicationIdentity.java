package com.felix.aigate.security;

/**
 * 认证通过后的 Application 身份。
 *
 * <p>只携带业务身份字段，绝不包含 apiKey / secret / keyHash / keyId / defaultDeploymentId，
 * 以便安全地放入 Spring SecurityContext 而不会泄露凭据。
 */
public record ApplicationIdentity(
        Long applicationId,
        String applicationName,
        Long teamId
) {
}
