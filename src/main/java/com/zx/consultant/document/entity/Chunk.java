package com.zx.consultant.document.entity;

import lombok.Data;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;

/**
 * 切块实体类
 * Chunk
 */
@Data
@TableName("chunk")
public class Chunk{
    
    /**
     * 主键 ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联的文档 ID (外键)
     */
    private Long documentId;

    /**
     * 切块序号 (对应你之前的 index)
     */
    private Integer chunkIndex;

    /**
     * 切块所在页码 (保留你原有的 page 字段，这对 PDF 溯源非常有用)
     */
    private Integer page;

    /**
     * 切块的文本内容
     */
    private String content;

    /**
     * Token 数量
     */
    private Integer tokenSize;

    /**
     * 向量数据库中对应的数据 ID
     */
    private String vectorId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}