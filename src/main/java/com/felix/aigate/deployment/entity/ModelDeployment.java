package com.felix.aigate.deployment.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@TableName("model_deployment")
public class ModelDeployment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long providerId;

    private Long modelId;

    private String endpointUrl;

    private String remoteModelName;

    /** PUT 为全量更新：credential 传 null 时也要落库清空，故该列更新时始终参与。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String encryptedCredential;

    private Boolean enabled;

    private Instant createdAt;

    private Instant updatedAt;
}