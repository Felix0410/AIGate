package com.felix.aigate.provider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.felix.aigate.provider.entity.Provider;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProviderMapper extends BaseMapper<Provider> {
}