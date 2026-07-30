package com.zx.consultant.document.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zx.consultant.common.exception.BaseException;
import com.zx.consultant.common.exception.MaxUploadSizeExceededException;
import com.zx.consultant.common.result.PageResult;
import com.zx.consultant.document.entity.Document;
import com.zx.consultant.document.enums.DocumentStatus;
import com.zx.consultant.document.mapper.DocumentMapper;
import com.zx.consultant.document.service.ChunkService;
import com.zx.consultant.document.service.DocumentProcessService;
import com.zx.consultant.document.service.DocumentService;
import com.zx.consultant.knowledge.entity.KnowledgeBase;
import com.zx.consultant.knowledge.mapper.KnowledgeBaseMapper;
import com.zx.consultant.knowledge.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 文档服务实现类
 * 负责文档的增删改查和相关业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    /** 单文件上传大小上限：50MB */
    private static final long MAX_UPLOAD_SIZE_BYTES = 50L * 1024 * 1024;

    private final DocumentProcessService documentProcessService;
    private final KnowledgeBaseMapper knowledgeBaseMapper; // 用于越权校验
    private final KnowledgeBaseService knowledgeBaseService;
    private final ChunkService chunkService;


    /**
     * 上传文档
     * @param knowledgeId
     * @param file
     * @param userId
     * @return
     */
    @Override
    public Long uploadDocument(Long knowledgeId, MultipartFile file, Long userId) {
        // 1. 越权校验：判断知识库是否属于当前用户
        checkKnowledgeOwnership(knowledgeId, userId);

        // 2. 文件大小校验：超过 50MB 拒绝上传
        if (file == null || file.isEmpty()) {
            throw new BaseException("上传文件不能为空");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
            throw new MaxUploadSizeExceededException(
                    "上传文件超出大小限制，最大支持50MB，当前文件大小: " + file.getSize() + " 字节");
        }

        // 3. 将文件保存到本地磁盘（或 OSS）
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String storagePath = saveFileToDisk(file); // 抽取出的存文件方法

        // 4. 构建并保存 Document 实体
        Long documentId = saveDocumentRecord(knowledgeId, originalFilename, fileExtension, file.getSize(), storagePath);

        /*注意这里将保存document抽离为了另一个方法，以此保证插入数据的事务提交了之后，
        才调用异步方法，进行查询，此时数据一定在数据库中保存了 */

        // 5. 关键：调用 ProcessService 异步执行 AI 解析入库流程
        documentProcessService.processDocumentAsync(documentId);

        // 6. 立即返回文档ID给前端，前端可轮询状态
        return documentId;
    }

    /**
     * 删除文档
     * @param documentId
     * @param userId
     * 外层编排方法：绝对不能加 @Transactional
     */
    @Override
    public void deleteDocument(Long documentId, Long userId) {
        // 获取文档信息（如路径），此时不涉及修改，可以放外面
        Document document = checkDocumentOwnership(documentId, userId);
        String storagePath = document.getStoragePath();

        // 【步骤 1, 2, 3】：调用内部带事务的方法，执行 MySQL 删除并强制提交
        // 如果这里报错，异常会抛出，下面 4 和 5 根本不会执行，文件得以安全保留
        this.deleteDatabaseRecords(documentId);

        // 代码只要能安然无恙走到这里，说明步骤 3 已经完成，MySQL 事务确确实实 commit 了！
        try {
            // 【步骤 4】：删除文件。即使失败，仅仅是产生了一个无害的垃圾文件
            deleteFileFromDisk(storagePath);
        } catch (Exception e) {
            log.error("文档 DB 已删除，但物理文件删除失败，留下孤儿文件路径: {}", storagePath, e);
        }

        // 【步骤 5】：异步删除向量
        // 放在最后，不阻塞主线程返回
        documentProcessService.deleteVectorsAsync(documentId);
    }



    /**
     * 重新构建索引
     * 只触发一个异步入口，内部串行：删 chunk -> 删向量 -> 重建
     */
    @Override
    public void reindex(Long documentId, Long userId) {
        Document document = checkDocumentOwnership(documentId, userId);
        document.setStatus(DocumentStatus.PARSING.name());
        this.updateById(document);
        log.info("重新构建索引：documentId:{}", documentId);
        documentProcessService.reindexDocumentAsync(documentId);
    }

    /**
     * 查询文档列表
     * @param knowledgeId
     * @param page
     * @param size
     * @param userId
     * @return
     */
    @Override
    public PageResult<Document> listDocuments(Long knowledgeId, Integer page, Integer size, Long userId) {
        checkKnowledgeOwnership(knowledgeId, userId);

        Page<Document> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Document::getKnowledgeId, knowledgeId)
                    .orderByDesc(Document::getCreateTime);

        Page<Document> resultPage = this.page(pageParam, queryWrapper);
        
        return new PageResult<>(resultPage.getTotal(), resultPage.getRecords());
    }

    /**
     * 查询文档详情
     */
    @Override
    public Document getDocument(Long documentId, Long userId) {
        return checkDocumentOwnership(documentId, userId);
    }

    /**
     * 获取文档状态
     * @param documentId
     * @param userId
     * @return
     */
    @Override
    public String getStatus(Long documentId, Long userId) {
        Document document = checkDocumentOwnership(documentId, userId);
        return document.getStatus();
    }

    /**
     * 获取文档名称
     * @param documentId
     * @return
     */
    @Override
    public String getDocumentName(Long documentId){
        Document document = this.getById(documentId);
        return document != null ? document.getFileName() : "未知文档";
    }


    // ================== 私有辅助方法 ==================

    /**
     * 检查知识库所有权
     * @param knowledgeId
     * @param userId
     */
    private void checkKnowledgeOwnership(Long knowledgeId, Long userId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeId);
        if (kb == null || !kb.getUserId().equals(userId)) {
            throw new BaseException("知识库不存在或无权操作");
        }
    }

    /**
     * 检查文档所有权
     * @param documentId
     * @param userId
     * @return
     */
    private Document checkDocumentOwnership(Long documentId, Long userId) {
        Document document = this.getById(documentId);
        if (document == null) {
            throw new BaseException("文档不存在");
        }
        checkKnowledgeOwnership(document.getKnowledgeId(), userId);
        return document;
    }

    /**
     * 保存文件到磁盘
     * @param file
     * @return
     */
    private String saveFileToDisk(MultipartFile file) {
        // TODO(V2): 替换为你实际的存储逻辑 (本地路径或阿里云OSS)
        try {
            String dirPath = System.getProperty("user.dir") + "/uploads/";
            File dir = new File(dirPath);
            if (!dir.exists()) dir.mkdirs();
            
            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            File dest = new File(dirPath + fileName);
            file.transferTo(dest);
            return dest.getAbsolutePath();
        } catch (IOException e) {
            log.error("文件存储失败", e);
            throw new BaseException("文件上传失败");
        }
    }

    /**
     * 保存文档记录
     * @param knowledgeId
     * @param fileName
     * @param fileType
     * @param fileSize
     * @param storagePath
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveDocumentRecord(Long knowledgeId, String fileName, String fileType,
                                   Long fileSize, String storagePath) {
        Document document = new Document();
        document.setKnowledgeId(knowledgeId);
        document.setFileName(fileName);
        document.setFileType(fileType);
        document.setFileSize(fileSize);
        document.setStoragePath(storagePath);
        document.setStatus(DocumentStatus.UPLOADING.name());
        this.save(document);
        // 文档记录落库后，知识库文档数 +1（与删除知识库时的 documentCount 校验对齐）
        knowledgeBaseService.incrementDocumentCount(knowledgeId);
        return document.getId();
    }


    /**
     * 内层数据库方法：专门负责 MySQL 操作，必须加 @Transactional
     * @param documentId
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDatabaseRecords(Long documentId) {
        Document document = this.getById(documentId);
        Long knowledgeId = document != null ? document.getKnowledgeId() : null;

        // 1. 删除 Chunk
        chunkService.deleteByDocumentId(documentId);
        // 2. 删除 Document
        this.removeById(documentId);
        // 3. 知识库文档数 -1
        if (knowledgeId != null) {
            knowledgeBaseService.decrementDocumentCount(knowledgeId);
        }
        // 4. 方法结束，Spring 自动 commit 事务
    }
    
    /**
     * 删除文件从磁盘
     * @param path
     */
    private void deleteFileFromDisk(String path) {
        if (path != null) {
            File file = new File(path);
            if (file.exists()) file.delete();
        }
    }

    /**
     * 获取文件扩展名
     * @param filename
     * @return
     */
    private String getFileExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        }
        return "unknown";
    }
}