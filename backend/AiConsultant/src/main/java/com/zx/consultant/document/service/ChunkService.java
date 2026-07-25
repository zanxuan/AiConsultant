package com.zx.consultant.document.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zx.consultant.document.dto.ParsedDocument;
import com.zx.consultant.document.entity.Chunk;
import java.util.List;

/**
 * 文本切块服务接口
 */
public interface ChunkService extends IService<Chunk> {

    /**
     * 切块文本并保存入库
     *
     * @param documentId 关联的文档 ID
     * @param text       需要切块的原始长文本
     * @return 包含完整数据库 ID 的切块列表
     */
    List<Chunk> chunkAndSaveText(Long documentId, ParsedDocument parsedDocument);


    /**
     * 删除数据库中的chunk元数据
     * @param documentId
     */
    void deleteByDocumentId(Long documentId);
}