package com.felix.aigate.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateModelRequest {

    @NotBlank
    @Size(max = 100)
    private String name;
}