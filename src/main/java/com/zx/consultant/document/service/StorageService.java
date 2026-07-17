package com.zx.consultant.document.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务
 */
public interface StorageService{
    /**
     * 上传文件
     * @param file
     * @return
     */
    String upload(MultipartFile file);
    
    /**
     * 删除文件
     * @param storagePath
     */
    void delete(String storagePath);
}