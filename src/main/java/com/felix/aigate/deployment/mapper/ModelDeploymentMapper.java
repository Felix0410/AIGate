package com.felix.aigate.deployment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.felix.aigate.deployment.entity.ModelDeployment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelDeploymentMapper extends BaseMapper<ModelDeployment> {
}