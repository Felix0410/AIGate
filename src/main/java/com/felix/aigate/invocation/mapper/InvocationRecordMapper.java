package com.felix.aigate.invocation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.felix.aigate.invocation.entity.InvocationRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

@Mapper
public interface InvocationRecordMapper extends BaseMapper<InvocationRecord> {

    @Delete("""
            DELETE FROM invocation_record
            WHERE created_at < #{cutoff}
            """)
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}