package com.tinglan.test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简历指标①验证:「20 条滑动窗口控制上下文,节省约 40% Token」。
 *
 * <p>方法:同一段 30 轮确定性对话脚本,分别按三种策略组装每轮请求的上下文——
 * 不做窗口(全量)/ 100 条窗口(应用当前值)/ 20 条窗口,累计对比每轮请求的
 * Prompt Token 总量。</p>
 *
 * <ul>
 *   <li>{@link #tokenSaveEstimate()} 离线粗估:免费、无网络依赖,随时可跑,给数量级结论</li>
 *   <li>{@link #tokenSaveRealApi()} 真实计量:调真实 LLM 读取接口返回的 usage 字段,
 *       约 90 次请求(30 轮 × 3 策略),手动运行,精确数字以它为准</li>
 * </ul>
 */
class TokenWindowSavingTest {

    private static final int TURNS = 30;
    private static final int WINDOW = 20;
    private static final String[] TOPICS = {
            "头晕", "失眠", "颈椎不适", "胃胀", "膝盖疼", "血压偏高",
            "感冒咳嗽", "眼睛干涩", "腰酸", "耳鸣", "心悸", "皮肤过敏",
            "偏头痛", "咽喉肿痛", "手指麻木"
    };

    /** 生成第 i 轮(0 起)对话脚本:内容确定、长度贴近真实问诊,保证两种策略可比。 */
    private static String userLine(int i) {
        String topic = TOPICS[i % TOPICS.length];
        return "我第" + (i + 1) + "次咨询:最近" + (i + 3) + "天一直" + topic
                + ",早上起床时最明显,下午会好一些,晚上偶尔加重,"
                + "没有发烧,食欲和睡眠一般,请问这可能是什么问题?需要做哪些检查?";
    }

    private static String assistantLine(int i) {
        String topic = TOPICS[i % TOPICS.length];
        return "您好,关于您描述的" + topic + "情况:结合持续时间" + (i + 3) + "天来看,"
                + "多数情况与疲劳和姿势习惯有关,建议先观察作息、避免久坐和熬夜,"
                + "多饮水、清淡饮食;如果症状持续超过一到两周,或出现加重的趋势,"
                + "建议到医院相应科室就诊,必要时做进一步检查。以上建议仅供参考,"
                + "如需明确诊断请以医生面诊为准,我也可以帮您查询号源并预约挂号。";
    }

    private static List<ChatMessage> script() {
        List<ChatMessage> all = new ArrayList<>();
        for (int i = 0; i < TURNS; i++) {
            all.add(new UserMessage(userLine(i)));
            all.add(new AiMessage(assistantLine(i)));
        }
        return all;
    }

    /** 窗口策略:取最后 maxMessages 条消息(与 MessageWindowChatMemory 语义一致)。 */
    private static List<ChatMessage> window(List<ChatMessage> history, int maxMessages) {
        return history.subList(Math.max(0, history.size() - maxMessages), history.size());
    }

    /** 离线粗估:免费,无网络依赖。 */
    @Test
    void tokenSaveEstimate() {
        List<ChatMessage> script = script();
        long totalFull = 0, totalWin100 = 0, totalWin20 = 0;

        for (int turn = 0; turn < TURNS; turn++) {
            // 第 turn 轮请求的上下文 = 前 turn 轮历史 + 本轮用户消息(脚本中的助手回复代表"历史")
            List<ChatMessage> context = script.subList(0, 2 * turn + 1);

            totalFull += context.stream().mapToLong(m -> TestSupport.estimateTokens(TestSupport.textOf(m))).sum();
            totalWin100 += window(context, 100).stream().mapToLong(m -> TestSupport.estimateTokens(TestSupport.textOf(m))).sum();
            totalWin20 += window(context, WINDOW).stream().mapToLong(m -> TestSupport.estimateTokens(TestSupport.textOf(m))).sum();
        }

        System.out.printf("%n===== Token 窗口对比(离线粗估,%d 轮对话)=====%n", TURNS);
        System.out.printf("不控制上下文(全量) : %,d tokens%n", totalFull);
        System.out.printf("100 条窗口          : %,d tokens(节省 %.1f%%)%n", totalWin100, pct(totalFull, totalWin100));
        System.out.printf("%d 条窗口            : %,d tokens(节省 %.1f%%)%n", WINDOW, totalWin20, pct(totalFull, totalWin20));

        assertTrue(totalWin20 < totalFull, "窗口策略应显著减少累计 Prompt Token");
    }

    /** 真实计量:调真实 LLM 读取 usage,精确数字以本用例为准。手动去掉 @Disabled 运行。 */
    @Disabled("消耗真实 Token:去掉此注解手动运行,约 90 次请求")
    @Test
    void tokenSaveRealApi() {
        ChatModel model = OpenAiChatModel.builder()
                .baseUrl(TestSupport.llmBaseUrl())
                .apiKey(TestSupport.llmApiKey())
                .modelName(TestSupport.llmModelName())
                .logRequests(false)
                .logResponses(false)
                .build();

        List<ChatMessage> script = script();
        long totalFull = 0, totalWin20 = 0;

        for (int turn = 0; turn < TURNS; turn++) {
            List<ChatMessage> context = script.subList(0, 2 * turn + 1);
            totalFull += promptTokens(model, context);
            totalWin20 += promptTokens(model, window(context, WINDOW));
        }

        System.out.printf("%n===== Token 窗口对比(真实 API usage,%d 轮对话)=====%n", TURNS);
        System.out.printf("不控制上下文(全量) : %,d prompt tokens%n", totalFull);
        System.out.printf("%d 条窗口            : %,d prompt tokens(节省 %.1f%%)%n", WINDOW, totalWin20, pct(totalFull, totalWin20));

        assertTrue(totalWin20 < totalFull, "窗口策略应显著减少累计 Prompt Token");
    }

    private static long promptTokens(ChatModel model, List<ChatMessage> context) {
        var response = model.chat(context);
        var usage = response.tokenUsage();
        if (usage == null) {
            // 个别兼容端点不回 usage 时退化为粗估,保证脚本不中断
            return context.stream().mapToLong(m -> TestSupport.estimateTokens(TestSupport.textOf(m))).sum();
        }
        return usage.inputTokenCount();
    }

    private static double pct(long base, long value) {
        return (base - value) * 100.0 / base;
    }
}
