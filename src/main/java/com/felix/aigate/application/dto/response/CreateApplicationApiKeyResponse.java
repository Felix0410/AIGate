package com.felix.aigate.application.dto.response;

import com.felix.aigate.application.entity.ApplicationApiKeyStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class CreateApplicationApiKeyResponse {

    private Long id;

    private Long applicationId;

    private String name;

    private String keyId;

    private String keyPrefix;

    private ApplicationApiKeyStatus status;

    private Instant createdAt;

    private Instant revokedAt;

    /** 完整明文 Key 仅在此 Create Response 中返回一次，普通 Response 不含该字段。 */
    private String apiKey;
}
