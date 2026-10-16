package com.felix.aigate.application.dto.response;

import com.felix.aigate.application.entity.ApplicationApiKeyStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class ApplicationApiKeyResponse {

    private Long id;

    private Long applicationId;

    private String name;

    private String keyId;

    private String keyPrefix;

    private ApplicationApiKeyStatus status;

    private Instant createdAt;

    private Instant revokedAt;
}
