package com.felix.aigate.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class ApplicationResponse {

    private Long id;

    private String name;

    private Long teamId;

    private Long defaultDeploymentId;

    private Instant createdAt;

    private Instant updatedAt;

}