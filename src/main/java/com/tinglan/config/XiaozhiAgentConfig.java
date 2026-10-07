package com.tinglan.config;

import com.tinglan.store.MongoChatMemoryStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;


@Configuration
public class XiaozhiAgentConfig {

    @Autowired
    private MongoChatMemoryStore mongoChatMemoryStore;

    @Bean
    public ChatMemoryProvider chatMemoryProviderXiaozhi() {
        return memoryId -> MessageWindowChatMemory
                .builder()
                .id(memoryId)
                .maxMessages(50)
                .chatMemoryStore(mongoChatMemoryStore)
                .build();
    }

//    @Bean
//    ContentRetriever contentRetrieverXiaozhi() {
//        // 读取知识库文档
//        Document document1 = FileSystemDocumentLoader.loadDocument("E:\\IDEACode\\langchain4j\\knowledge\\医院信息.md");
//        Document document2 = FileSystemDocumentLoader.loadDocument("E:\\IDEACode\\langchain4j\\knowledge\\科室信息.md");
//        Document document3 = FileSystemDocumentLoader.loadDocument("E:\\IDEACode\\langchain4j\\knowledge\\神经内科.md");
//        List<Document> documents = Arrays.asList(document1, document2, document3);
//
//        // 使用内存向量存储
//        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
//
//        // 使用默认分割器进行向量化入库
//        EmbeddingStoreIngestor.ingest(documents, embeddingStore);
//
//        // 从向量存储中检索与查询相关的内容
//        return EmbeddingStoreContentRetriever.from(embeddingStore);
//    }

    @Autowired
    private EmbeddingStore embeddingStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Bean
    ContentRetriever contentRetrieverXiaozhiPincone() {
        return EmbeddingStoreContentRetriever.builder()
                // 设置用于生成嵌入向量的模型
                .embeddingModel(embeddingModel)
                // 指定使用的向量存储
                .embeddingStore(embeddingStore)
                // 最多返回 1 条匹配结果
                .maxResults(1)
                // 最小得分阈值
                .minScore(0.8)
                .build();
    }

}
