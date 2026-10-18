package com.felix.aigate.invocation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.felix.aigate.invocation.entity.InvocationRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InvocationRecordMapper extends BaseMapper<InvocationRecord> {
}