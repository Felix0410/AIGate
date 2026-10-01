package com.felix.aigate.team.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class TeamResponse {

    private Long id;

    private String name;

    private Instant createdAt;

    private Instant updatedAt;

}
