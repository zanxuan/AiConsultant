package com.zx.consultant.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.consultant.knowledge.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库 Mapper 接口
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {
    // 基础的增删改查已经由 BaseMapper 提供
    // 如果后续有复杂的多表联查，可以在这里定义方法并在 XML 中写 SQL
}