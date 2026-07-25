package com.zx.consultant.document.service;

import java.util.List;
import com.zx.consultant.document.entity.Chunk;

// 4. 向量化服务 (EmbeddingService)
public interface EmbeddingService {
    /**
     * 调用阿里大模型，将文本块转为浮点数向量
     * @param chunks 文本块列表
     * @return
     */
    List<List<Double>> getEmbeddings(List<Chunk>  chunks);
}