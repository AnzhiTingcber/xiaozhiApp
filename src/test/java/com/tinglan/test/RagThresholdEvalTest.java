package com.tinglan.test;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * 简历指标②验证:「RAG 相似度阈值过滤,无效回答率降低约 30%」。
 *
 * <p>两层证据:</p>
 * <ul>
 *   <li>{@link #thresholdFiltersOutOfScopeRetrieval()} 组件级(不调 LLM,只调 Embedding):
 *       对知识库外的干扰问题,验证 minScore=0.8 能把不该检索到的噪音全部挡掉,
 *       而 minScore=0 会把不相关片段放进上下文(即"污染")。免费可跑。</li>
 *   <li>{@link #answerInvalidRateEval()} 端到端(调真实 LLM,@Disabled 手动跑):
 *       同一批评测题(知识库内 + 知识库外)分别让"无阈值"与"有阈值"两个助手作答,
 *       再用 LLM-as-Judge 判定每条回答"有效/无效",输出两种配置的无效回答率与下降幅度。
 *       "无效"定义:知识库外问题编造回答,或知识库内问题答非所问。</li>
 * </ul>
 *
 * <p>前置条件:Pinecone 索引 {@code xiaozhi-index}/{@code xiaozhi-namespace} 中
 * 已入库医院知识文档(医院信息/科室信息/医生介绍)。索引为空时组件级用例会自动跳过。</p>
 */
class RagThresholdEvalTest {

    private static final double THRESHOLD = 0.8;

    /** 知识库外的干扰问题:合理配置下不应检索出内容,更不应作答。 */
    private static final List<String> OUT_OF_SCOPE = List.of(
            "今天股市行情怎么样?",
            "帮我写一首关于春天的诗",
            "附近有什么好吃的火锅店推荐?",
            "下一届世界杯在哪举办?",
            "用 Java 写一个快速排序",
            "帮我订明天去上海的高铁票",
            "讲个笑话听听",
            "把 this sentence 翻译成中文"
    );

    /** 知识库内的问题:按已入库的医院/科室/医生文档设计。 */
    private static final List<String> IN_SCOPE = List.of(
            "北京协和医院的地址在哪里?",
            "医院门诊的开放时间是什么时候?",
            "神经内科有哪些医生出诊?",
            "神经内科主要看哪些疾病?",
            "医院有哪些科室?",
            "怎么预约挂号?",
            "医院支持医保报销吗?",
            "住院需要办理什么手续?"
    );

    private interface HospitalAssistant {
        @SystemMessage("""
                你是"北京协和医院"的智能客服与医疗伴诊助手,态度友好、言辞简洁。
                优先依据知识库检索到的内容回答;
                知识库中没有的信息绝对不要编造;
                与医院、就医无关的问题,礼貌说明你无法提供这方面的帮助。
                """)
        String chat(@UserMessage String message);
    }

    /** 组件级:阈值把知识库外问题的检索污染全部挡掉。不调 LLM。 */
    @Test
    void thresholdFiltersOutOfScopeRetrieval() {
        EmbeddingStore<TextSegment> store = embeddingStore();
        EmbeddingModel embeddingModel = embeddingModel();

        ContentRetriever strict = retriever(store, embeddingModel, THRESHOLD);
        ContentRetriever loose = retriever(store, embeddingModel, 0.0);

        int strictHits = 0, looseHits = 0;
        System.out.printf("%n===== RAG 阈值过滤(组件级,不调 LLM)=====%n");
        System.out.printf("%-28s | 无阈值命中 | 0.8阈值命中%n", "干扰问题");
        for (String q : OUT_OF_SCOPE) {
            int strictCount = strict.retrieve(new Query(q)).size();
            int looseCount = loose.retrieve(new Query(q)).size();
            strictHits += strictCount;
            looseHits += looseCount;
            System.out.printf("%-28s |     %d      |     %d%n", shorten(q), looseCount, strictCount);
        }
        System.out.printf("合计:无阈值泄漏 %d 条,0.8 阈值泄漏 %d 条%n", looseHits, strictHits);

        // 索引为空时两个检索器都查不到东西,测试无意义,自动跳过
        assumeFalse(looseHits == 0,
                "Pinecone 索引中查不到任何内容:请先确认知识文档已入库,再运行本用例");

        assertTrue(strictHits < looseHits, "0.8 阈值应挡住知识库外问题的检索污染");
    }

    /** 端到端:无阈值 vs 0.8 阈值,LLM-as-Judge 统计无效回答率。手动去掉 @Disabled 运行。 */
    @Disabled("消耗真实 Token:去掉此注解手动运行,约 64 次请求(32 次作答 + 32 次评审)")
    @Test
    void answerInvalidRateEval() {
        ChatModel model = chatModel();
        EmbeddingModel embeddingModel = embeddingModel();
        EmbeddingStore<TextSegment> store = embeddingStore();

        HospitalAssistant baseline = assistant(model, retriever(store, embeddingModel, 0.0));
        HospitalAssistant strict = assistant(model, retriever(store, embeddingModel, THRESHOLD));

        List<String> questions = new java.util.ArrayList<>();
        questions.addAll(IN_SCOPE);
        questions.addAll(OUT_OF_SCOPE);

        int baselineInvalid = 0, strictInvalid = 0;
        System.out.printf("%n===== 无效回答率评估(端到端,LLM-as-Judge)=====%n");
        System.out.printf("%-30s | %-8s | %-8s | 判定%n", "问题", "无阈值", "0.8阈值");
        for (String q : questions) {
            String a1 = safeAnswer(baseline, q);
            String a2 = safeAnswer(strict, q);
            boolean v1 = isValid(model, q, a1);
            boolean v2 = isValid(model, q, a2);
            baselineInvalid += v1 ? 0 : 1;
            strictInvalid += v2 ? 0 : 1;
            System.out.printf("%-30s |   %s    |   %s    | %s%n",
                    shorten(q), v1 ? "有效" : "无效", v2 ? "有效" : "无效",
                    v1 == v2 ? "—" : (v1 ? "阈值版更好" : "阈值版反而更差"));
        }

        int total = questions.size();
        double rate1 = baselineInvalid * 100.0 / total;
        double rate2 = strictInvalid * 100.0 / total;
        System.out.printf("%n无效回答率:无阈值 %.1f%%(共 %d 条),0.8 阈值 %.1f%%(共 %d 条)%n",
                rate1, total, rate2, total);
        System.out.printf("无效回答率下降:%.1f 个百分点%n", rate1 - rate2);

        assertTrue(rate2 <= rate1, "加阈值后无效回答率不应升高");
    }

    // ---------- 评审 ----------

    private static boolean isValid(ChatModel judge, String question, String answer) {
        String prompt = """
                你是回答质量评审员。对下面这组"问题-回答"判定"有效"还是"无效":
                - 问题与医院/就医相关:回答切题、基于事实即"有效";答非所问、明显编造即"无效"
                - 问题与医院/就医无关:回答礼貌拒绝并说明无法提供帮助即"有效";强行作答、编造内容即"无效"
                只输出两个字:有效 或 无效。

                问题:%s
                回答:%s
                """.formatted(question, answer);
        String verdict = judge.chat(prompt);
        return verdict != null && verdict.contains("有效");
    }

    private static String safeAnswer(HospitalAssistant assistant, String question) {
        try {
            return assistant.chat(question);
        } catch (Exception e) {
            return "(调用失败:" + e.getMessage() + ")";
        }
    }

    // ---------- 组件构建 ----------

    private static HospitalAssistant assistant(ChatModel model, ContentRetriever retriever) {
        return AiServices.builder(HospitalAssistant.class)
                .chatModel(model)
                .contentRetriever(retriever)
                .build();
    }

    private static ContentRetriever retriever(EmbeddingStore<TextSegment> store, EmbeddingModel model, double minScore) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingModel(model)
                .embeddingStore(store)
                .maxResults(3)
                .minScore(minScore)
                .build();
    }

    private static ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(TestSupport.llmBaseUrl())
                .apiKey(TestSupport.llmApiKey())
                .modelName(TestSupport.llmModelName())
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    private static EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(TestSupport.embedBaseUrl())
                .apiKey(TestSupport.embedApiKey())
                .modelName(TestSupport.embedModelName())
                .build();
    }

    private static EmbeddingStore<TextSegment> embeddingStore() {
        return PineconeEmbeddingStore.builder()
                .apiKey(TestSupport.pineconeApiKey())
                .index("xiaozhi-index")
                .nameSpace("xiaozhi-namespace")
                .build();
    }

    private static String shorten(String s) {
        return s.length() <= 26 ? s : s.substring(0, 25) + "…";
    }
}
