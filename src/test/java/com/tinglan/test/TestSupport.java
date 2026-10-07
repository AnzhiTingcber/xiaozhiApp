package com.tinglan.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * 测试辅助:从 src/main/resources/application.yml 读取模型与 Pinecone 配置,
 * 避免在测试代码里硬编码任何密钥。
 *
 * <p>只支持本项目的两层级配法结构,用极简缩进解析,不额外引入 YAML 依赖。</p>
 */
final class TestSupport {

    private static final Path YML = Path.of("src/main/resources/application.yml");
    private static volatile Map<String, String> cached;

    private TestSupport() {
    }

    private static Map<String, String> props() {
        if (cached == null) {
            synchronized (TestSupport.class) {
                if (cached == null) {
                    cached = parse(YML);
                }
            }
        }
        return cached;
    }

    static String llmBaseUrl()        { return props().get("langchain4j.open-ai.streaming-chat-model.base-url"); }
    static String llmApiKey()         { return props().get("langchain4j.open-ai.streaming-chat-model.api-key"); }
    static String llmModelName()      { return props().get("langchain4j.open-ai.streaming-chat-model.model-name"); }
    static String embedBaseUrl()      { return props().get("langchain4j.open-ai.embedding-model.base-url"); }
    static String embedApiKey()       { return props().get("langchain4j.open-ai.embedding-model.api-key"); }
    static String embedModelName()    { return props().get("langchain4j.open-ai.embedding-model.model-name"); }
    static String pineconeApiKey()    { return props().get("pinecone.api-key"); }

    /** ChatMessage 接口没有统一的 text():按实际类型取文本(测试里只构造 UserMessage/AiMessage 两种)。 */
    static String textOf(ChatMessage m) {
        if (m instanceof UserMessage user) {
            return user.singleText();
        }
        if (m instanceof AiMessage ai) {
            return ai.text() == null ? "" : ai.text();
        }
        return "";
    }

    /**
     * 粗略估算一段文本的 Token 数(GLM 系分词经验值:1 个汉字 ≈ 0.6 token,ASCII ≈ 0.3 token/字符)。
     * 仅用于离线对比测试,精确计量请跑 @Disabled 的真实 API 用例。
     */
    static long estimateTokens(String text) {
        int cjk = 0, ascii = 0, other = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if ((cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0x3400 && cp <= 0x4DBF)
                    || (cp >= 0x3000 && cp <= 0x303F) || (cp >= 0xFF00 && cp <= 0xFFEF)) {
                cjk++;
            } else if (cp < 128) {
                ascii++;
            } else {
                other++;
            }
        }
        return Math.round(cjk * 0.6 + ascii * 0.3 + other * 0.6);
    }

    private static Map<String, String> parse(Path yml) {
        Map<String, String> result = new HashMap<>();
        Deque<String[]> stack = new ArrayDeque<>(); // 元素:[indent, key]
        try {
            for (String rawLine : Files.readAllLines(yml, StandardCharsets.UTF_8)) {
                String line = rawLine.stripLeading();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int indent = rawLine.length() - line.length();
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = line.substring(0, colon).strip();
                String value = line.substring(colon + 1);
                int comment = value.indexOf('#');
                if (comment >= 0) {
                    value = value.substring(0, comment);
                }
                value = value.strip();

                while (!stack.isEmpty() && Integer.parseInt(stack.peek()[0]) >= indent) {
                    stack.pop();
                }
                StringBuilder fullKey = new StringBuilder();
                // ArrayDeque 遍历顺序是"头→尾"(最近压入→最外层),键路径需"最外层→最近层",故反向迭代
                Iterator<String[]> iter = stack.descendingIterator();
                while (iter.hasNext()) {
                    fullKey.append(iter.next()[1]).append('.');
                }
                fullKey.append(key);
                stack.push(new String[]{String.valueOf(indent), key});

                if (!value.isEmpty()) {
                    result.put(fullKey.toString(), value);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 " + yml + " 失败(请在项目根目录运行测试)", e);
        }
        return result;
    }
}
