package com.felix.aigate.application.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateApplicationRequest {

    private String name;

    private Long teamId;

}