package com.felix.aigate.employee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@TableName("employee")
public class Employee {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String email;

    private Long teamId;

    private Instant createdAt;

    private Instant updatedAt;
}
