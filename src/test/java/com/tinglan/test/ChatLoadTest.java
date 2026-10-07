package com.tinglan.test;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简历指标③验证：「本地 JMeter 压测，支持 50+ 并发」。
 *
 * <p>用 JDK 自带 HttpClient + 线程池模拟并发用户，直接打流式对话接口
 * {@code POST /xiaozhi/chat}，统计 QPS、平均/P95 延迟、错误率。
 * 不依赖 JMeter（JMeter 的线程组+聚合报告等价于本类的输出），随项目进仓库，IDEA 里直接跑。</p>
 *
 * <p>运行前置条件：</p>
 * <ul>
 *   <li>应用已启动（IDEA 运行 XzhiApp，端口 8080 可改）</li>
 *   <li>MySQL / MongoDB / LLM 可用（工具调用与记忆会真实发生）</li>
 * </ul>
 *
 * <p>参数（IDEA 运行配置加 VM options）：</p>
 * <pre>
 * -Dconcurrency=50            并发线程数
 * -Drounds=4                  每线程请求数（总请求数 = 50 × 4 = 200）
 * -Dchatload.base-url=http://localhost:8080
 * </pre>
 *
 * <p>关于「QPS 100+」口径的说明：全链路（真实 LLM）下单次响应秒级，
 * 50 并发下 QPS 上限约为 并发数 ÷ 平均延迟；要验证应用接口层 100+ QPS，
 * 把 LLM 端点指向本地 mock（或把 base-url 指向纯 echo 服务）再压，应用层吞吐才不受模型推理时长限制。</p>
 */
@Disabled("压测用例：先在 IDEA 启动 XzhiApp，再手动运行；会真实消耗 Token 并写库")
class ChatLoadTest {

    private static final String BASE_URL = System.getProperty("chatload.base-url", "http://localhost:8080");
    private static final int CONCURRENCY = Integer.getInteger("concurrency", 50);
    private static final int ROUNDS = Integer.getInteger("rounds", 4);
    /** 每请求独享的会话 ID 起点，避免 50 个线程挤爆同一份记忆窗口 */
    private static final long MEMORY_ID_BASE = 10_000_000L;

    private record LatencySample(long millis, boolean ok) {
    }

    @Test
    void loadChatEndpoint() throws Exception {
        int total = CONCURRENCY * ROUNDS;
        List<LatencySample> samples = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger errors = new AtomicInteger();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        // 预检：应用没起来时快速失败，避免打出 200 个连接异常污染数据
        preflight(client);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(total);

        for (int t = 0; t < CONCURRENCY; t++) {
            final int threadNo = t;
            pool.submit(() -> {
                try {
                    startGate.await();
                    for (int r = 0; r < ROUNDS; r++) {
                        long begin = System.currentTimeMillis();
                        boolean ok = false;
                        try {
                            long memoryId = MEMORY_ID_BASE + threadNo * ROUNDS + r;
                            ok = postOnce(client, memoryId, "你好").isBlank() ? false : true;
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                        samples.add(new LatencySample(System.currentTimeMillis() - begin, ok));
                        doneGate.countDown();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        long wallBegin = System.currentTimeMillis();
        startGate.countDown();
        boolean finished = doneGate.await(5, TimeUnit.MINUTES);
        long wallMs = System.currentTimeMillis() - wallBegin;
        pool.shutdown();

        report(wallMs, total, finished, samples, errors.get());
    }

    /** 发起一次流式对话并读完整条流，返回正文（非空=成功）。 */
    private static String postOnce(HttpClient client, long memoryId, String message) throws Exception {
        String body = "{\"memoryId\":" + memoryId + ",\"message\":\"" + message + "\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/xiaozhi/chat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(3))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        // ofString() 会消费完整条流（流式接口要读完才返回），即模拟用户完整体验
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static void preflight(HttpClient client) {
        try {
            String body = postOnce(client, MEMORY_ID_BASE - 1, "你好");
            System.out.println("预检通过，收到首条响应（前 40 字）：" + body.substring(0, Math.min(40, body.length())) + "…");
        } catch (Exception e) {
            throw new IllegalStateException("应用未就绪：" + e + " —— 请先在 IDEA 启动 XzhiApp 再运行本用例", e);
        }
    }

    private static void report(long wallMs, int total, boolean finished, List<LatencySample> samples, int errors) {
        List<Long> okLatencies = new ArrayList<>();
        long sum = 0;
        for (LatencySample s : samples) {
            if (s.ok()) {
                okLatencies.add(s.millis());
            }
        }
        Collections.sort(okLatencies);
        long max = samples.stream().mapToLong(LatencySample::millis).max().orElse(0);

        System.out.printf("%n===== 并发压测报告 =====%n");
        System.out.printf("目标        : %s/xiaozhi/chat%n", BASE_URL);
        System.out.printf("并发模型    : %d 线程 × %d 轮 = %d 请求%n", CONCURRENCY, ROUNDS, total);
        System.out.printf("完成状态    : %s%n", finished ? "全部完成" : "5 分钟超时未完成");
        System.out.printf("成功/失败   : %d / %d（错误率 %.1f%%）%n",
                okLatencies.size(), errors, errors * 100.0 / Math.max(1, total));
        System.out.printf("QPS         : %.1f（成功请求 ÷ 总耗时 %.1fs）%n",
                okLatencies.size() * 1000.0 / wallMs, wallMs / 1000.0);
        System.out.printf("成功延迟(ms): 平均 %.0f | P50 %d | P95 %d | 最大 %d%n",
                okLatencies.stream().mapToLong(Long::longValue).average().orElse(0),
                percentile(okLatencies, 50), percentile(okLatencies, 95), max);
    }

    private static long percentile(List<Long> sortedAsc, int p) {
        if (sortedAsc.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil(p / 100.0 * sortedAsc.size()) - 1;
        return sortedAsc.get(Math.max(0, Math.min(idx, sortedAsc.size() - 1)));
    }
}
