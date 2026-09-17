package com.tinglan.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore;
import dev.langchain4j.store.embedding.pinecone.PineconeServerlessIndexConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingStoreConfig {

    @Value("${pinecone.api-key}")
    private String pineconeApiKey;

    @Autowired
    private EmbeddingModel embeddingModel; //向量维度与 embeddingModel 保持一致

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        // 创建向量存储
        EmbeddingStore<TextSegment> embeddingStore = PineconeEmbeddingStore.builder()
                .apiKey(pineconeApiKey)
                .index("xiaozhi-index") // 若指定索引不存在，会自动创建
                .nameSpace("xiaozhi-namespace") // 若指定命名空间不存在，也会自动创建
                .createIndex(PineconeServerlessIndexConfig.builder()
                        .cloud("AWS")
                        .region("us-east-1")
                        .dimension(embeddingModel.dimension()) // 向量维度与 embeddingModel 保持一致
                        .build())
                .build();
        return embeddingStore;
    }
}