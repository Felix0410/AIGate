package com.felix.aigate.runtime.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 统一模型调用请求契约。
 *
 * <p>不携带 applicationId / providerId / modelId / deploymentId：客户端身份由 P2-T05
 * {@code ApplicationIdentity} 决定，模型目标由后续 {@code Application.defaultDeploymentId} 决定。
 */
@Getter
@Setter
public class InvokeRequest {

    @NotEmpty
    @Valid
    private List<InvokeMessage> messages;

    /** 可选，允许为 null，不设默认值。 */
    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private Double temperature;

    /** 可选，允许为 null，不设默认值。 */
    @Positive
    private Integer maxTokens;
}
