package com.zx.consultant.chat.service;

import com.zx.consultant.chat.dto.ChatReq;
import com.zx.consultant.chat.dto.ChatResp;

/**
 * ChatService
 */
public interface ChatService {

    /**
     * 问答
     * @param req
     * @return
     */
    public ChatResp ask(ChatReq req);

   

}