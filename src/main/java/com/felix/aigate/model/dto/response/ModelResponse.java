package com.felix.aigate.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class ModelResponse {

    private Long id;

    private String name;

    private Instant createdAt;

    private Instant updatedAt;
}