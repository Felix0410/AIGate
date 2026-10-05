package com.felix.aigate.application.entity;

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
@TableName("application")
public class Application {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long teamId;

    private Long defaultDeploymentId;

    private Instant createdAt;

    private Instant updatedAt;

}