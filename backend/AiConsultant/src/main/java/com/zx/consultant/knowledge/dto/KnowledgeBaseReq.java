package com.zx.consultant.knowledge.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "知识库创建/修改请求参数")
public class KnowledgeBaseReq {

    @NotBlank(message = "知识库名称不能为空")
    @Schema(description = "知识库名称", example = "Java研发知识库")
    private String name;

    @Schema(description = "知识库描述", example = "存放Java基础与进阶研发规范")
    private String description;
}