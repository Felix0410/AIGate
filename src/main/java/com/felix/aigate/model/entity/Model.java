package com.felix.aigate.model.entity;

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
@TableName("model")
public class Model {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Instant createdAt;

    private Instant updatedAt;
}