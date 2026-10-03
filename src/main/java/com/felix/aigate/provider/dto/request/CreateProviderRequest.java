package com.felix.aigate.provider.dto.request;

import com.felix.aigate.provider.entity.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateProviderRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private ProviderType type;
}