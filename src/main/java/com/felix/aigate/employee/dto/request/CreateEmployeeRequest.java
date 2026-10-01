package com.felix.aigate.employee.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateEmployeeRequest {

    private String name;

    private String email;

    private Long teamId;

}
