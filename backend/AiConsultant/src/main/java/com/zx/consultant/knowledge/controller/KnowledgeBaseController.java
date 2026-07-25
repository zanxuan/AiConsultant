package com.zx.consultant.knowledge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.zx.consultant.common.constant.MessageConstant;
import com.zx.consultant.common.result.PageResult;
import com.zx.consultant.common.result.Result;
import com.zx.consultant.common.utils.BaseContext;
import com.zx.consultant.knowledge.dto.KnowledgeBaseReq;
import com.zx.consultant.knowledge.vo.KnowledgeBaseVO;
import com.zx.consultant.knowledge.service.KnowledgeBaseService;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
@RequiredArgsConstructor
@Tag(name = "知识库管理模块", description = "知识库的增删改查接口")
public class KnowledgeBaseController {

    @Autowired
     private  KnowledgeBaseService knowledgeBaseService;


    /**
     * 创建知识库
     * @param request
     * @return
     */
    @PostMapping
    @Operation(summary = "1. 创建知识库", description = "新建一个知识库，需要提供名称和描述")
    public Result<Long> createKnowledgeBase(@Validated @RequestBody KnowledgeBaseReq request) {
         Long id = knowledgeBaseService.create(request,BaseContext.getCurrentId());
         return Result.success(id);
    }

    /**
     * 查询知识库列表
     * @param keyword
     * @param pageNum
     * @param pageSize
     * @return
     */
    @GetMapping
    @Operation(summary = "2. 查询知识库列表", description = "支持分页和关键字搜索")
    public Result<PageResult<KnowledgeBaseVO>> getKnowledgeBaseList(
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer pageSize) {
        
        PageResult<KnowledgeBaseVO> page = knowledgeBaseService.getPage(keyword, pageNum, pageSize, BaseContext.getCurrentId());
        return Result.success(page);
    }

    /**
     * 获取知识库详情
     * @param knowledgeId
     * @return
     */
    @GetMapping("/{knowledgeId}")
    @Operation(summary = "3. 获取知识库详情", description = "根据 ID 获取知识库详细信息")
    public Result<KnowledgeBaseVO> getKnowledgeBaseDetail(
            @Parameter(description = "知识库ID", required = true) 
            @PathVariable("knowledgeId") Long knowledgeId) {
        
         KnowledgeBaseVO detail = knowledgeBaseService.getDetail(knowledgeId);
        return Result.success(detail);
    }

    /**
     * 修改知识库
     * @param knowledgeId
     * @param request
     * @return
     */
    @PutMapping("/{knowledgeId}")
    @Operation(summary = "4. 修改知识库", description = "根据 ID 修改知识库的基本信息")
    public Result<Void> updateKnowledgeBase(
            @Parameter(description = "知识库ID", required = true) 
            @PathVariable("knowledgeId") Long knowledgeId,
            @Validated @RequestBody KnowledgeBaseReq request) {
        
         knowledgeBaseService.update(knowledgeId, request, BaseContext.getCurrentId());
        return Result.success(MessageConstant.SUCCESS);
    }

    /**
     * 删除知识库
     * @param knowledgeId
     * @return
     */
    @DeleteMapping("/{knowledgeId}")
    @Operation(summary = "5. 删除知识库", description = "根据 ID 删除指定的知识库")
    public Result<Void> deleteKnowledgeBase(
            @Parameter(description = "知识库ID", required = true) 
            @PathVariable("knowledgeId") Long knowledgeId) {

        knowledgeBaseService.delete(knowledgeId, BaseContext.getCurrentId());
        return Result.success(MessageConstant.SUCCESS);
    }
}