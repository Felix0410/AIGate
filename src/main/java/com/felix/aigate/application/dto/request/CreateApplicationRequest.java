package com.felix.aigate.application.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateApplicationRequest {

    private String name;

    private Long teamId;

}