package com.zx.consultant.rag.eval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.consultant.rag.eval.entity.RagEvalRetrievalTrace;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评测检索链路明细 Mapper。增删改查由 BaseMapper 提供。
 */
@Mapper
public interface RagEvalRetrievalTraceMapper extends BaseMapper<RagEvalRetrievalTrace> {
}
