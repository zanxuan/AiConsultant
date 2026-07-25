package com.zx.consultant.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充字段处理器
 */
@Component
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时的填充策略
     * 对应 @TableField(fill = FieldFill.INSERT)
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        // 自动填充 createTime，类型为 LocalDateTime
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        
        // 自动填充 updateTime，因为插入时通常也需要一个初始的更新时间
        // 对应 @TableField(fill = FieldFill.INSERT_UPDATE) 在插入时也会触发
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    /**
     * 更新时的填充策略
     * 对应 @TableField(fill = FieldFill.UPDATE) 或 FieldFill.INSERT_UPDATE
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // 自动填充 updateTime，类型为 LocalDateTime
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加分页插件，这里以 MySQL 为例。如果你使用的是其他数据库（如 PostgreSQL, Oracle），请修改 DbType
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }


   
}