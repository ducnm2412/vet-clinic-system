package com.vetclinic.chat.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.vetclinic.chat.config.ChatProperties;
import com.vetclinic.chat.exception.ChatDisabledException;
import com.vetclinic.chat.exception.GeminiUnavailableException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Gọi REST generateContent của Gemini. API key đi trong header x-goog-api-key (không nằm trên URL
 * nên không lọt vào log truy cập).
 *
 * Gemini bản miễn phí hay quá tải (503) hoặc treo đến hết timeout, nên không thử từng model nối
 * tiếp nhau (cộng dồn thời gian chờ). Thay vào đó: model đầu chạy trước; quá hedgeSeconds chưa có
 * kết quả thì chạy thêm model kế tiếp SONG SONG; model nào lỗi thì chuyển ngay sang model kế tiếp.
 * Lấy kết quả thành công đầu tiên, bỏ phần còn lại. Cả quá trình bị chặn bởi totalTimeoutSeconds.
 *
 * Không ghi log nội dung hội thoại, chỉ ghi tên model và mã lỗi HTTP.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final ChatProperties.Gemini config;
    private final RestClient restClient;
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "gemini-call");
        thread.setDaemon(true);
        return thread;
    });

    public GeminiClient(ChatProperties properties) {
        this.config = properties.gemini();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(config.timeoutSeconds() * 1_000);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(config.baseUrl())
                .build();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public boolean isEnabled() {
        return config.enabled();
    }

    /**
     * Gửi một yêu cầu generateContent và trả nguyên phản hồi JSON.
     *
     * @throws ChatDisabledException      chưa có API key
     * @throws GeminiUnavailableException mọi model đều lỗi hoặc quá hạn chót
     */
    public JsonNode generate(JsonNode body) {
        if (!config.enabled()) {
            throw new ChatDisabledException();
        }
        List<String> models = config.allModels();
        long hedgeMillis = TimeUnit.SECONDS.toMillis(config.hedgeSeconds());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(config.totalTimeoutSeconds());

        ExecutorCompletionService<JsonNode> completion = new ExecutorCompletionService<>(executor);
        List<Future<JsonNode>> started = new ArrayList<>();
        int launched = 0;
        int running = 0;

        try {
            launched = launch(completion, started, models, launched, body);
            running++;

            while (running > 0) {
                long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remainingMillis <= 0) {
                    break;
                }
                boolean moreModels = launched < models.size();
                Future<JsonNode> done = completion.poll(
                        moreModels ? Math.min(hedgeMillis, remainingMillis) : remainingMillis, TimeUnit.MILLISECONDS);

                if (done == null) {
                    // Chưa model nào xong: chạy thêm model kế tiếp song song (hoặc hết hạn chót ở vòng sau).
                    if (moreModels) {
                        launched = launch(completion, started, models, launched, body);
                        running++;
                    }
                    continue;
                }

                running--;
                try {
                    return done.get();
                } catch (ExecutionException e) {
                    // Model này lỗi: chuyển ngay sang model kế tiếp, không chờ hết nhịp hedge.
                    if (launched < models.size()) {
                        launched = launch(completion, started, models, launched, body);
                        running++;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            started.forEach(future -> future.cancel(true));
        }
        throw new GeminiUnavailableException("Mọi model Gemini đều không phản hồi được.");
    }

    private int launch(ExecutorCompletionService<JsonNode> completion, List<Future<JsonNode>> started,
                       List<String> models, int index, JsonNode body) {
        String model = models.get(index);
        Callable<JsonNode> call = () -> callModel(model, body);
        started.add(completion.submit(call));
        return index + 1;
    }

    private JsonNode callModel(String model, JsonNode body) {
        try {
            JsonNode response = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", config.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new IllegalStateException("Phản hồi rỗng");
            }
            log.info("Gemini model {} đã trả lời", model);
            return response;
        } catch (RestClientResponseException e) {
            log.warn("Gemini model {} lỗi HTTP {}", model, e.getStatusCode().value());
            throw e;
        } catch (RuntimeException e) {
            log.warn("Gemini model {} lỗi {}", model, e.getClass().getSimpleName());
            throw e;
        }
    }
}
