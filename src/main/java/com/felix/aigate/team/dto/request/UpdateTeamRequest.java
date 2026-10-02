package com.felix.aigate.team.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTeamRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

}
