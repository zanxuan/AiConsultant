package com.zx.consultant.document.service;

import org.springframework.web.multipart.MultipartFile;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zx.consultant.common.result.PageResult;
import com.zx.consultant.document.entity.Document;

// 1. 文档主服务 (负责统筹流程)
public interface DocumentService extends IService<Document> {
    /**
     * 上传文档
     * @param knowledgeId
     * @param file
     * @param userId
     * @return
     */ 
    Long uploadDocument(Long knowledgeId, MultipartFile file, Long userId);


    /**
     * 删除文档
     * @param documentId
     * @param userId
     */
    void deleteDocument(Long documentId, Long userId);


    /**
     * 重新构建索引
     * @param documentId
     * @param userId
     */
    void reindex(Long documentId, Long userId);

    /**
     * 查询文档列表
     * @param knowledgeId
     * @param page
     * @param size
     * @param userId
     * @return
     */
    PageResult<Document> listDocuments(Long knowledgeId, Integer page, Integer size, Long userId);

    /**
     * 查询文档详情（含越权校验）
     * @param documentId
     * @param userId
     * @return
     */
    Document getDocument(Long documentId, Long userId);
    
    /**
     * 获取文档状态
     * @param documentId
     * @param userId
     * @return
     */
    String getStatus(Long documentId, Long userId);


    /**
     * 获取文档名称
     * @param documentId
     * @return
     */
    String getDocumentName(Long documentId);
}
