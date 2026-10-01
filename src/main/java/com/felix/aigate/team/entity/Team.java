package com.felix.aigate.team.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.Instant;

@TableName("team")
@Getter
@Setter
@NoArgsConstructor
public class Team {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Instant createdAt;

    private Instant updatedAt;
}
