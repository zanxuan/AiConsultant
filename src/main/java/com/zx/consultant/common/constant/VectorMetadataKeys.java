package com.zx.consultant.common.constant;


/**
 * 向量数据库元数据 Key 常量池
 */
public final class VectorMetadataKeys {
    
    // 私有化构造器，防止被实例化
    private VectorMetadataKeys() {}

    public static final String DOCUMENT_ID = "documentId";
    public static final String PAGE = "page";
    public static final String CHUNK_INDEX = "chunkIndex";
    // 以后如果加了如 author, title 等，都在这里统一管理
}