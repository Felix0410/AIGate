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
@TableName("application_api_key")
public class ApplicationApiKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long applicationId;

    private String name;

    private String keyId;

    private String keyPrefix;

    private String keyHash;

    private ApplicationApiKeyStatus status;

    private Instant createdAt;

    private Instant revokedAt;
}
