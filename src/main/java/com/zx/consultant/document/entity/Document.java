package com.zx.consultant.document.entity;

import lombok.Data;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;

/**
 * 文档实体类
 * Document
 */
@Data
@TableName("document")
public class Document {
    
    /**
     * 主键 ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的知识库ID */
    private Long knowledgeId;

    private String fileName;

    /** 文件类型：PDF, MD, TXT */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 磁盘/OSS存储路径 */
    private String storagePath;

    /** 状态：UPLOADING, PARSING, INDEXING, READY, FAILED */
    private String status;

    /** 切块数量 */
    private Integer chunkCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}