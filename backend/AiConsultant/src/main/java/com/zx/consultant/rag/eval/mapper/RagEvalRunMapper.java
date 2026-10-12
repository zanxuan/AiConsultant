package com.zx.consultant.rag.eval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.consultant.rag.eval.entity.RagEvalRun;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评测运行记录 Mapper。增删改查由 BaseMapper 提供。
 */
@Mapper
public interface RagEvalRunMapper extends BaseMapper<RagEvalRun> {
}
