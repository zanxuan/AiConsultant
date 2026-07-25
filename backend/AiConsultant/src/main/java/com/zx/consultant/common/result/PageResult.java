package com.zx.consultant.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 通用分页返回结果
 *
 * @param <T> 具体的列表数据类型
 */
@Data
@NoArgsConstructor
@Schema(description = "通用分页结果封装")
public class PageResult<T> implements Serializable {

    @Schema(description = "总记录数", example = "100")
    private Long total;

    @Schema(description = "当前页码", example = "1")
    private Long pageNum;

    @Schema(description = "每页大小", example = "10")
    private Long pageSize;

    @Schema(description = "总页数", example = "10")
    private Long pages;

    @Schema(description = "分页数据列表")
    private List<T> list;

    /**
     * 全参数构造函数
     */
    public PageResult(Long total, Long pageNum, Long pageSize, Long pages, List<T> list) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = pages;
        this.list = list != null ? list : Collections.emptyList();
    }

    /**
     * 快捷构造：直接传入总数和列表
     */
    public PageResult(Long total, List<T> list) {
        this.total = total;
        this.list = list != null ? list : Collections.emptyList();
    }

    /**
     * ✨ 核心高频用法：直接从 MyBatis-Plus 的 IPage 对象转换
     * 
     * @param page MyBatis-Plus 的分页结果对象
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        if (page == null) {
            return new PageResult<>(0L, 0L, 0L, 0L, Collections.emptyList());
        }
        return new PageResult<>(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getPages(),
                page.getRecords()
        );
    }

    /**
     * ✨ 高阶用法：当数据库查出来的 Entity 列表需要转换为 VO 列表时使用
     * 
     * @param page MyBatis-Plus 的分页结果对象 (通常泛型是 Entity)
     * @param voList 已经经过转换的 VO 列表
     */
    public static <T, E> PageResult<T> of(IPage<E> page, List<T> voList) {
        if (page == null) {
            return new PageResult<>(0L, 0L, 0L, 0L, Collections.emptyList());
        }
        return new PageResult<>(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getPages(),
                voList
        );
    }
}