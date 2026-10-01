package com.felix.aigate.employee.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class EmployeeResponse {

    private Long id;

    private String name;

    private String email;

    private Long teamId;

    private Instant createdAt;

    private Instant updatedAt;

}
