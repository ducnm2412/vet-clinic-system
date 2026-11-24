package com.vetclinic.chat.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vetclinic.chat.config.ChatProperties;
import com.vetclinic.chat.gemini.GeminiEmbeddingClient.Result;
import com.vetclinic.chat.gemini.GeminiEmbeddingClient.Status;
import com.vetclinic.chat.support.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/** Giả lập Gemini embedContent bằng HTTP server cục bộ: header, thân yêu cầu, timeout, 429, ngắt mạch, trần, cache. */
class GeminiEmbeddingClientTest {

    private static final int DIM = 4;

    private final ObjectMapper mapper = new ObjectMapper();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-24T03:00:00Z")); // 10:00 giờ VN

    private HttpServer server;
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final List<String> keys = new CopyOnWriteArrayList<>();
    private final List<JsonNode> bodies = new CopyOnWriteArrayList<>();
    /** Mã HTTP lần trả lời kế tiếp, độ trễ ms, thân phản hồi lỗi. */
    private volatile int status = 200;
    private volatile int delayMillis = 0;
    private volatile String errorBody = "{\"error\":{\"code\":500}}";
    private volatile int dimensionReturned = DIM;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            keys.add(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            bodies.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            String json;
            if (status == 200) {
                StringBuilder values = new StringBuilder();
                for (int i = 0; i < dimensionReturned; i++) {
                    values.append(i == 0 ? "" : ",").append(i + 1);
                }
                json = "{\"embedding\":{\"values\":[" + values + "]}}";
            } else {
                json = errorBody;
            }
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (IOException ignored) {
                // client đã bỏ vì timeout
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ChatProperties.A2 settings(int dailyCap, int timeoutMs, int cacheSize) {
        return new ChatProperties.A2(true, "", 0.6, 3, dailyCap, 500, timeoutMs, cacheSize, 1, 3, 60);
    }

    private GeminiEmbeddingClient client(String apiKey, ChatProperties.A2 settings) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta";
        ChatProperties.Gemini gemini = new ChatProperties.Gemini(apiKey, baseUrl, "m", List.of(), 15, 3, 25);
        return GeminiEmbeddingClient.create(gemini, settings, "gemini-embedding-2", "CLASSIFICATION", DIM, clock);
    }

    private GeminiEmbeddingClient client() {
        return client("key-1", settings(900, 1200, 100));
    }

    @Test
    void sendsKeyInHeaderAndBodyPerContract() {
        Result result = client().embed("Mèo nhà em bỏ ăn hai ngày");

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.values()).containsExactly(1, 2, 3, 4);
        assertThat(paths).containsExactly("/v1beta/models/gemini-embedding-2:embedContent");
        assertThat(keys).containsExactly("key-1");
        JsonNode body = bodies.get(0);
        assertThat(body.at("/content/parts/0/text").asText()).isEqualTo("Mèo nhà em bỏ ăn hai ngày");
        assertThat(body.path("taskType").asText()).isEqualTo("CLASSIFICATION");
        assertThat(body.path("outputDimensionality").asInt()).isEqualTo(DIM);
    }

    @Test
    void noApiKeyNeverCallsNetwork() {
        Result result = client("", settings(900, 1200, 100)).embed("giá tiêm phòng cho chó");

        assertThat(result.status()).isEqualTo(Status.NO_API_KEY);
        assertThat(paths).isEmpty();
    }

    @Test
    void blankTextNeverCallsNetwork() {
        assertThat(client().embed("   ").status()).isEqualTo(Status.EMPTY_TEXT);
        assertThat(paths).isEmpty();
    }

    @Test
    void slowResponseTimesOutQuickly() {
        delayMillis = 3_000;
        GeminiEmbeddingClient client = client("k", settings(900, 300, 100));

        long start = System.nanoTime();
        Result result = client.embed("mèo bỏ ăn hai ngày rồi");
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(millis).isLessThan(2_000);
    }

    @Test
    void wrongDimensionCountsAsFailure() {
        dimensionReturned = DIM + 1;

        assertThat(client().embed("mèo bỏ ăn hai ngày rồi").status()).isEqualTo(Status.FAILED);
    }

    @Test
    void cacheHitDoesNotCallAgain() {
        GeminiEmbeddingClient client = client();

        assertThat(client.embed("Giá tiêm phòng  cho chó").status()).isEqualTo(Status.OK);
        // Khác hoa/thường và khoảng trắng → cùng khoá cache.
        Result again = client.embed("  giá tiêm phòng cho CHÓ ");

        assertThat(again.status()).isEqualTo(Status.CACHED);
        assertThat(again.values()).containsExactly(1, 2, 3, 4);
        assertThat(paths).hasSize(1);
        assertThat(client.callsToday()).isEqualTo(1);
        // Dấu tiếng Việt vẫn phân biệt: câu khác dấu là câu khác.
        assertThat(client.embed("gia tiem phong cho cho").status()).isEqualTo(Status.OK);
        assertThat(paths).hasSize(2);
    }

    @Test
    void cacheIsLeastRecentlyUsed() {
        GeminiEmbeddingClient client = client("k", settings(900, 1200, 2));
        client.embed("câu số một nè");
        client.embed("câu số hai nè");
        client.embed("câu số một nè"); // dùng lại → câu hai thành cũ nhất
        client.embed("câu số ba nè");  // đẩy câu hai ra

        assertThat(client.embed("câu số một nè").status()).isEqualTo(Status.CACHED);
        assertThat(client.embed("câu số hai nè").status()).isEqualTo(Status.OK);
    }

    @Test
    void dailyCapStopsCallsUntilNextVietnamDay() {
        GeminiEmbeddingClient client = client("k", settings(2, 1200, 0));

        assertThat(client.embed("câu thứ nhất nè").status()).isEqualTo(Status.OK);
        assertThat(client.embed("câu thứ hai nè").status()).isEqualTo(Status.OK);
        assertThat(client.embed("câu thứ ba nè").status()).isEqualTo(Status.DAILY_CAP);
        assertThat(client.isAvailable()).isFalse();
        assertThat(paths).hasSize(2);

        // 10:00 → 23:59 giờ VN vẫn là hôm nay
        clock.advance(Duration.ofHours(13).plusMinutes(59));
        assertThat(client.embed("câu thứ ba nè").status()).isEqualTo(Status.DAILY_CAP);
        // qua 0 giờ VN → ngày mới
        clock.advance(Duration.ofMinutes(2));
        assertThat(client.embed("câu thứ ba nè").status()).isEqualTo(Status.OK);
    }

    @Test
    void http429PausesA2ForConfiguredMinutes() {
        GeminiEmbeddingClient client = client();
        status = 429;
        errorBody = "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"per minute\"}}";

        assertThat(client.embed("câu thứ nhất nè").status()).isEqualTo(Status.RATE_LIMITED);
        status = 200;
        assertThat(client.embed("câu thứ hai nè").status()).isEqualTo(Status.RATE_LIMITED);
        assertThat(client.isAvailable()).isFalse();
        assertThat(paths).hasSize(1); // lần sau không gọi mạng

        clock.advance(Duration.ofSeconds(50));
        assertThat(client.embed("câu thứ hai nè").status()).isEqualTo(Status.RATE_LIMITED);
        clock.advance(Duration.ofSeconds(15));
        assertThat(client.embed("câu thứ hai nè").status()).isEqualTo(Status.OK);
    }

    @Test
    void dailyQuota429OnlyPausesForConfiguredMinutesToo() {
        // Không tắt A2 tới nửa đêm Pacific nữa: đổi key / nâng gói phải có tác dụng sau ~1 phút.
        GeminiEmbeddingClient client = client();
        status = 429;
        errorBody = "{\"error\":{\"code\":429,\"details\":[{\"violations\":[{\"quotaId\":"
                + "\"EmbedContentRequestsPerDayPerUserPerProjectPerModel-FreeTier\"}]}]}}";

        assertThat(client.embed("câu thứ nhất nè").status()).isEqualTo(Status.RATE_LIMITED);
        status = 200;
        assertThat(client.embed("câu thứ hai nè").status()).isEqualTo(Status.RATE_LIMITED);
        clock.advance(Duration.ofSeconds(61));
        assertThat(client.embed("câu thứ hai nè").status()).isEqualTo(Status.OK);
    }

    @Test
    void consecutiveServerErrorsOpenTheCircuit() {
        GeminiEmbeddingClient client = client();
        status = 503;

        assertThat(client.embed("câu số một nè").status()).isEqualTo(Status.FAILED);
        assertThat(client.embed("câu số hai nè").status()).isEqualTo(Status.FAILED);
        assertThat(client.embed("câu số ba nè").status()).isEqualTo(Status.FAILED);
        assertThat(client.embed("câu số bốn nè").status()).isEqualTo(Status.CIRCUIT_OPEN);
        assertThat(paths).hasSize(3);

        status = 200;
        clock.advance(Duration.ofSeconds(61));
        assertThat(client.embed("câu số bốn nè").status()).isEqualTo(Status.OK);
    }

    @Test
    void successResetsFailureCounter() {
        GeminiEmbeddingClient client = client();
        status = 500;
        client.embed("câu số một nè");
        client.embed("câu số hai nè");
        status = 200;
        client.embed("câu số ba nè");
        status = 500;
        client.embed("câu số bốn nè");
        client.embed("câu số năm nè");

        assertThat(client.embed("câu số sáu nè").status()).isEqualTo(Status.FAILED); // mới 3 lỗi liên tiếp ở lần này
        assertThat(client.embed("câu số bảy nè").status()).isEqualTo(Status.CIRCUIT_OPEN);
    }

    @Test
    void unreachableServerFailsFastWithoutThrowing() {
        GeminiEmbeddingClient client = client();
        server.stop(0);

        assertThat(client.embed("mèo bỏ ăn hai ngày rồi").status()).isEqualTo(Status.FAILED);
    }
}
