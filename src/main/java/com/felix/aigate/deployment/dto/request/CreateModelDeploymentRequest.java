package com.felix.aigate.deployment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateModelDeploymentRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private Long providerId;

    @NotNull
    private Long modelId;

    @NotBlank
    @Size(max = 500)
    private String endpointUrl;

    @NotBlank
    @Size(max = 255)
    private String remoteModelName;

    @NotNull
    private Boolean enabled;

    private String credential;
}