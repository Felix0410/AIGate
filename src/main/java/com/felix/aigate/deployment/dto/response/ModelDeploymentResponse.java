package com.felix.aigate.deployment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class ModelDeploymentResponse {

    private Long id;
    private String name;
    private Long providerId;
    private Long modelId;
    private String endpointUrl;
    private String remoteModelName;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}