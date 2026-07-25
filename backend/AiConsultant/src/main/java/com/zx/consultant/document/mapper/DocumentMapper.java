package com.zx.consultant.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.consultant.document.entity.Document; // 注意这里的 import
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
}