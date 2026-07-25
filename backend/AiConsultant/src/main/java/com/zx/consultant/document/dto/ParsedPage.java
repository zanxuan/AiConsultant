package com.zx.consultant.document.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 解析后的单页内容（用于 PDF 等按页溯源）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ParsedPage {

    /**
     * 页码（从 1 开始）
     */
    private int pageNumber;

    /**
     * 该页提取出的纯文本
     */
    private String content;
}
