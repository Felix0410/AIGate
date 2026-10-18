package com.felix.aigate.invocation.entity;

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
@TableName("invocation_record")
public class InvocationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long applicationId;

    private InvocationStatus status;

    private String errorCode;

    private Long durationMs;

    private Instant createdAt;
}