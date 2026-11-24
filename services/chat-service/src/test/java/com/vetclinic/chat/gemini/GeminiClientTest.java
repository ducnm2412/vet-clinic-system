package com.vetclinic.chat.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vetclinic.chat.config.ChatProperties;
import com.vetclinic.chat.exception.ChatDisabledException;
import com.vetclinic.chat.exception.GeminiUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Giả lập Gemini bằng một HTTP server cục bộ để kiểm tra thử model dự phòng và chạy song song. */
class GeminiClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    /** model -> (mã HTTP, độ trễ ms) */
    private final Map<String, int[]> behaviour = new ConcurrentHashMap<>();
    private final List<String> requestedModels = new CopyOnWriteArrayList<>();
    private final List<String> apiKeysSeen = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/", exchange -> {
            String path = exchange.getRequestURI().getPath(); // /v1beta/models/{model}:generateContent
            String model = path.substring(path.lastIndexOf('/') + 1).replace(":generateContent", "");
            requestedModels.add(model);
            apiKeysSeen.add(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            exchange.getRequestBody().readAllBytes();
            int[] plan = behaviour.getOrDefault(model, new int[]{404, 0});
            try {
                Thread.sleep(plan[1]);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            String json = plan[0] == 200
                    ? "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"tra loi tu " + model + "\"}]}}]}"
                    : "{\"error\":{\"code\":" + plan[0] + "}}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(plan[0], bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (IOException ignored) {
                // client đã bỏ (huỷ) — bình thường với model chậm bị bỏ qua
            } finally {
                exchange.close();
            }
        });
        // Mặc định HttpServer xử lý tuần tự: model "chậm" sẽ chặn model "nhanh" và test sai.
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private GeminiClient client(String apiKey, String model, List<String> fallbacks, int hedgeSeconds, int totalSeconds) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta";
        ChatProperties properties = new ChatProperties(20, 1000, 4, 45,
                new ChatProperties.Gemini(apiKey, baseUrl, model, fallbacks, 10, hedgeSeconds, totalSeconds),
                new ChatProperties.RateLimit(10, 20, 60, 3000),
                new ChatProperties.IntentSettings(ChatProperties.IntentMode.RULES, "", null, ChatProperties.A2.disabled()));
        return new GeminiClient(properties);
    }

    private String textOf(JsonNode response) {
        return response.at("/candidates/0/content/parts/0/text").asText();
    }

    @Test
    void firstModelAnswersAndFallbacksAreNotCalled() throws Exception {
        behaviour.put("a", new int[]{200, 0});
        GeminiClient client = client("key-1", "a", List.of("b"), 3, 10);

        JsonNode response = client.generate(mapper.readTree("{}"));

        assertThat(textOf(response)).isEqualTo("tra loi tu a");
        assertThat(requestedModels).containsExactly("a");
        assertThat(apiKeysSeen).containsExactly("key-1"); // key đi trong header x-goog-api-key
    }

    @Test
    void failingModelMovesImmediatelyToNextWithoutWaitingForHedge() throws Exception {
        behaviour.put("a", new int[]{503, 0});
        behaviour.put("b", new int[]{200, 0});
        GeminiClient client = client("k", "a", List.of("b"), 8, 20); // hedge 8s: nếu chờ hedge thì test sẽ chậm

        long start = System.nanoTime();
        JsonNode response = client.generate(mapper.readTree("{}"));
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(textOf(response)).isEqualTo("tra loi tu b");
        assertThat(millis).isLessThan(3_000);
    }

    @Test
    void slowModelIsHedgedWithFallbackRunningInParallel() throws Exception {
        behaviour.put("slow", new int[]{200, 6_000});
        behaviour.put("fast", new int[]{200, 0});
        GeminiClient client = client("k", "slow", List.of("fast"), 1, 20);

        long start = System.nanoTime();
        JsonNode response = client.generate(mapper.readTree("{}"));
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(textOf(response)).isEqualTo("tra loi tu fast");
        assertThat(millis).isBetween(900L, 3_500L); // ~hedge 1s, không chờ model chậm 6s
        assertThat(requestedModels).contains("slow", "fast");
    }

    @Test
    void allModelsFailingThrowsUnavailable() {
        behaviour.put("a", new int[]{503, 0});
        behaviour.put("b", new int[]{429, 0});
        GeminiClient client = client("k", "a", List.of("b"), 1, 10);

        assertThatThrownBy(() -> client.generate(mapper.readTree("{}")))
                .isInstanceOf(GeminiUnavailableException.class);
        assertThat(new ArrayList<>(requestedModels)).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void totalDeadlineStopsWaitingForHangingModels() {
        behaviour.put("a", new int[]{200, 8_000});
        GeminiClient client = client("k", "a", List.of(), 1, 2);

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.generate(mapper.readTree("{}")))
                .isInstanceOf(GeminiUnavailableException.class);
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(millis).isLessThan(4_500);
    }

    @Test
    void missingApiKeyDisablesTheClientWithoutAnyNetworkCall() {
        GeminiClient client = client("", "a", List.of("b"), 1, 5);

        assertThat(client.isEnabled()).isFalse();
        assertThatThrownBy(() -> client.generate(mapper.createObjectNode()))
                .isInstanceOf(ChatDisabledException.class);
        assertThat(requestedModels).isEmpty();
    }
}
