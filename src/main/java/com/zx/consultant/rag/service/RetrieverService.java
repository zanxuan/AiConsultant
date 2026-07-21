package com.zx.consultant.rag.service;

import java.util.List;

import com.zx.consultant.rag.entity.RetrievedChunk;

public interface RetrieverService {

    List<RetrievedChunk>  retrieve(String query);
}
