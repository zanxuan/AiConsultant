package com.zx.consultant.document.controller;
import com.zx.consultant.common.result.Result;
import com.zx.consultant.common.result.PageResult;
import com.zx.consultant.common.utils.BaseContext;
import com.zx.consultant.document.entity.Document;
import com.zx.consultant.document.service.DocumentService;
import com.zx.consultant.document.vo.DocumentUploadVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "文档模块", description = "文档生命周期管理（上传、解析、向量化）")
public class DocumentController {

    @Autowired
    private  DocumentService documentService;

    /**
     * 上传文档
     * @param knowledgeId
     * @param file
     * @return
     */
    @PostMapping("/upload")
    @Operation(summary = "1. 上传文档", description = "上传文档并触发解析流程")
    public Result<DocumentUploadVO> upload(
            @RequestParam("knowledgeId") Long knowledgeId,
            @RequestParam("file") MultipartFile file) {
        
        Long documentId = documentService.uploadDocument(knowledgeId, file, BaseContext.getCurrentId());
        // 按照接口设计返回 documentId 和初始状态
        DocumentUploadVO documentUploadVO = DocumentUploadVO.builder()
                .documentId(documentId)
                .status("UPLOADING")
                .build();
        return Result.success(documentUploadVO);
    }

    /**
     * 查询文档列表
     * @param knowledgeId
     * @param page
     * @param size
     * @return
     */

    @GetMapping
    @Operation(summary = "2. 查询文档列表")
    public Result<PageResult<Document>> list(
            @RequestParam("knowledgeId") Long knowledgeId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        
        PageResult<Document> pageResult = documentService.listDocuments(
                knowledgeId, page, size, BaseContext.getCurrentId());
        return Result.success(pageResult);
    }

    /**
     * 查询文档详情
     * @param documentId
     * @return
     */
    @GetMapping("/{documentId}")
    @Operation(summary = "3. 查询文档详情")
    public Result<Document> getDetail(@PathVariable("documentId") Long documentId) {
        Document document = documentService.getDocument(documentId, BaseContext.getCurrentId());
        return Result.success(document);
    }

    /**
     * 删除文档
     * @param documentId
     * @return
     */
    @DeleteMapping("/{documentId}")
    @Operation(summary = "4. 删除文档", description = "彻底删除 MySQL、Redis 及磁盘文件")
    public Result<Void> delete(@PathVariable("documentId") Long documentId) {
        documentService.deleteDocument(documentId, BaseContext.getCurrentId());
        return Result.success();
    }

    /**
     * 重新构建索引
     * @param documentId
     * @return
     */
    @PostMapping("/{documentId}/reindex")
    @Operation(summary = "5. 重新构建索引", description = "V1 预留：清理旧向量并重新走解析切块流程")
    public Result<Void> reindex(@PathVariable("documentId") Long documentId) {
        documentService.reindex(documentId, BaseContext.getCurrentId());
        return Result.success();
    }

    /**
     * 查询解析状态
     * @param documentId
     * @return
     */
    @GetMapping("/{documentId}/status")
    @Operation(summary = "6. 查询解析状态")
    public Result<String> getStatus(@PathVariable("documentId") Long documentId) {
        String status = documentService.getStatus(documentId, BaseContext.getCurrentId());
        return Result.success(status);
    }
}