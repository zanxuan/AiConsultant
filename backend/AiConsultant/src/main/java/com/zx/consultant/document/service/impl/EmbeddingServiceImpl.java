package com.zx.consultant.document.service.impl;

import com.zx.consultant.common.exception.BaseException;
import com.zx.consultant.document.service.EmbeddingService;
import com.zx.consultant.document.entity.Chunk;
import com.zx.consultant.document.mapper.ChunkMapper; // 假设您的 Mapper 包路径在此
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 向量化服务实现类
 * EmbeddingServiceImpl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    // 依赖注入：由 Spring 容器提供配置好的阿里 DashScope EmbeddingModel
    private final EmbeddingModel embeddingModel;
    
    // 注入 Chunk 的数据库持久化 Mapper
     private final ChunkMapper chunkMapper;

    

    /**
     * 获取向量并保存/更新 Chunk 状态
     * @param chunks 切块实体列表
     * @return 对应的 Double 向量列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class) // 涉及数据库批量更新，建议开启事务
    public List<List<Double>> getEmbeddings(List<Chunk> chunks) {
        log.info("开始执行向量化调用，待处理 Chunk 实体数量: {}", chunks == null ? 0 : chunks.size());

        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        try {
            // 1. 从 Chunk 实体中提取文本内容，并转换为大模型需要的 List<TextSegment>
            List<TextSegment> segments = chunks.stream()
                    .map(chunk -> TextSegment.from(chunk.getContent()))
                    .collect(Collectors.toList());

            // 2. 一键调用阿里大模型批量生成向量
            Response<List<Embedding>> response = embeddingModel.embedAll(segments);
            List<Embedding> embeddings = response.content();

            if (embeddings.size() != chunks.size()) {
                throw new BaseException("向量化返回结果数量与输入文本块数量不一致");
            }

            // 3. 将底层 AI 框架的 Float 向量转换为接口要求的 Double 向量
            List<List<Double>> result = embeddings.stream()
                    .map(embedding -> embedding.vectorAsList().stream()
                            .map(Float::doubleValue)
                            .collect(Collectors.toList()))
                    .collect(Collectors.toList());

            // 4. 将向量元数据（例如向量库 ID）关联回 Chunk 实体中，并更新到数据库
            for (int i = 0; i < chunks.size(); i++) {
                Chunk chunk = chunks.get(i);
                
                 // 预生成VectorId，后续写入向量库时使用
                // 如果您是后续在其他步骤写入向量库，可在此生成 UUID 作为唯一标识并绑定
                if (chunk.getVectorId() == null || chunk.getVectorId().isEmpty()) {
                    String generatedVectorId = UUID.randomUUID().toString().replace("-", "");
                    chunk.setVectorId(generatedVectorId);
                }

                // 注入并调用 Mapper 更新此 Chunk
                // 无论是更新（updateById）还是批量插入（根据您的业务设计选择，这里以 updateById 为例）
                chunkMapper.updateById(chunk);
            }

            log.info("向量化调用成功并已更新 Chunk 实体数据！");
            return result;

        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用阿里大模型向量化或保存数据库失败: ", e);
            throw new BaseException("文本向量化失败，请检查网络、大模型 API 配置或数据库连接");
        }
    }
}