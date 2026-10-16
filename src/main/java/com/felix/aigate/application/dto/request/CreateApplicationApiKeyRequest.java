package com.felix.aigate.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateApplicationApiKeyRequest {

    @NotBlank
    @Size(max = 100)
    private String name;
}
