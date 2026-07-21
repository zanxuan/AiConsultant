package com.zx.consultant.rag.entity;

import lombok.Data;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;

/**
 * 聊天记录实体类
 */
@Data
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private String role; // "user" or "assistant"
    private String content;
    private String reference; // 引用文档，例如：Redis最佳实践.pdf
    private Integer tokenUsage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}