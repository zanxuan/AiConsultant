package com.zx.consultant.chat.dto;

import com.zx.consultant.rag.dto.CitationDTO;
import lombok.Data;
import java.util.List;
/*
 * 聊天响应DTO
 * @author zx
 * @date 2026-07-20
 */
@Data
public class ChatResp {
    /**
     * 回答正文（不含参考文件信息）
     */
    private String answer;
    /**
     * 结构化引用来源列表，与 answer 同级返回
     */
    private List<CitationDTO> references;
}
