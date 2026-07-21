package com.zx.consultant.rag.service;

import java.util.List;

public interface RewriteService {

    String rewriteQuery(String originalQuery, List<String> memory);
}
