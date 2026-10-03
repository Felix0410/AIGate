package com.felix.aigate.provider.dto.response;

import com.felix.aigate.provider.entity.ProviderType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class ProviderResponse {

    private Long id;
    private String name;
    private ProviderType type;
    private Instant createdAt;
    private Instant updatedAt;
}