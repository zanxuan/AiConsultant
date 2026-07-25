package com.zx.consultant.chat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 聊天会话实体类
 * 作用：保存用户会话上下文，用于后续聊天历史回显、多知识库隔离以及会话的完整恢复。
 */
@Data
@TableName("conversation")
public class Conversation {
    
    /**
     * 主键，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户 ID (绑定具体用户，用于数据隔离)
     */
    private Long userId;
    
    /**
     * 关联的知识库 ID (指定该会话基于哪个知识库进行问答)
     */
    private Long knowledgeId;
    
    /**
     * 会话标题 (用于在前端侧边栏展示)
     */
    private String title;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    /**
     * 更新时间 (当会话有新消息时，可更新此字段，用于会话列表按最新活跃时间排序)
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}