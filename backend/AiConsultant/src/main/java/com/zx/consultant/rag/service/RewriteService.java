package com.zx.consultant.rag.service;

import java.util.List;

import com.zx.consultant.chat.entity.Message;

public interface RewriteService {

    String rewriteQuery(String originalQuery, List<Message> memory);
}
