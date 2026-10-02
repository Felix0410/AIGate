package com.felix.aigate.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

@Getter
@AllArgsConstructor
public class ApiErrorResponse {

    private String code;

    private String message;

    private Instant timestamp;

    private Map<String, String> errors;
}
