package com.zx.consultant.summary.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文档总结分组参数，对应 app.summary。长短文档由 fileSize 判定，切块由文档处理层负责。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.summary")
public class SummaryProperties {

    /** 与 document.file_size（字节）比较：不超过则视为短文档，直接加载原文总结。 */
    private int shortTextThreshold = 3000;

    /** 阶段性总结时每次合并的已有块数，对应原先「两块一组」。 */
    private int groupSize = 2;
}
