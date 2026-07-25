package com.zx.consultant.document.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 文档上传VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadVO{

    private Long documentId;

    private String status;

}