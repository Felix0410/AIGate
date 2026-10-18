package com.felix.aigate.invocation.dto;

import com.felix.aigate.invocation.entity.InvocationStatus;

import java.time.Instant;

public record InvocationRecordResponse(
        Long id,
        Long applicationId,
        InvocationStatus status,
        String errorCode,
        Long durationMs,
        Instant createdAt
) {
}