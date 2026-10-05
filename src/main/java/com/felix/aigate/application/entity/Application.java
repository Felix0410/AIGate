package com.felix.aigate.application.entity;

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
@TableName("application")
public class Application {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long teamId;

    /** PUT 为全量更新：defaultDeploymentId 传 null 时也要落库解绑，故该列更新时始终参与。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long defaultDeploymentId;

    private Instant createdAt;

    private Instant updatedAt;

}