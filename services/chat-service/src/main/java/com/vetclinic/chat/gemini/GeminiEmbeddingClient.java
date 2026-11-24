package com.vetclinic.chat.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vetclinic.chat.config.AppConfig;
import com.vetclinic.chat.config.ChatProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Gọi {@code POST {base-url}/models/{model}:embedContent} của Gemini cho tầng phân loại ý định A2, với các lớp
 * bảo vệ để A2 không bao giờ làm chatbot chậm hay tốn hạn mức vô ích:
 * <ul>
 *   <li>Timeout ngắn (mặc định kết nối 500 ms, đọc 1200 ms).</li>
 *   <li>Cache LRU theo câu đã chuẩn hoá nhẹ (NFC, chữ thường, gộp khoảng trắng — GIỮ dấu tiếng Việt vì
 *       "bán"/"bận"/"ban" khác nghĩa và embedding phân biệt được): câu lặp lại không gọi API.</li>
 *   <li>Trần số lần gọi mỗi ngày theo giờ Việt Nam (gói miễn phí ~1000 embed/ngày/model). Hết trần → bỏ qua tới
 *       ngày sau.</li>
 *   <li>HTTP 429: tạm dừng {@code rate-limit-pause-minutes}; nếu phản hồi nói hạn mức THEO NGÀY ("PerDay" trong
 *       thông báo/quotaId) thì dừng tới 0 giờ hôm sau theo giờ Thái Bình Dương (Google đặt lại hạn mức ngày lúc đó).</li>
 *   <li>Ngắt mạch: {@code failure-threshold} lỗi liên tiếp (5xx, timeout, lỗi mạng, phản hồi hỏng, 4xx khác 429)
 *       → bỏ qua {@code circuit-open-seconds}; một lần thành công đặt lại bộ đếm.</li>
 * </ul>
 * {@link #embed} không bao giờ ném lỗi: mọi trường hợp không có vector đều trả {@link Result} với trạng thái tương ứng.
 * API key đi trong header x-goog-api-key. Không ghi log nội dung tin nhắn, key hay thân phản hồi lỗi.
 */
public class GeminiEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingClient.class);

    /** Google đặt lại hạn mức theo ngày vào nửa đêm giờ Thái Bình Dương. */
    static final ZoneId QUOTA_RESET_ZONE = ZoneId.of("America/Los_Angeles");

    /** Lớp gửi HTTP; tách ra để test thay bằng bản giả. Lỗi HTTP ném {@link RestClientResponseException}. */
    @FunctionalInterface
    public interface Transport {
        JsonNode post(String model, JsonNode body);
    }

    public enum Status {
        OK, CACHED, NO_API_KEY, EMPTY_TEXT, DAILY_CAP, RATE_LIMITED, CIRCUIT_OPEN, FAILED
    }

    /** {@code values} chỉ khác null khi {@link #hasVector()}. */
    public record Result(Status status, double[] values) {
        public boolean hasVector() {
            return values != null;
        }

        static Result of(Status status) {
            return new Result(status, null);
        }
    }

    private final boolean hasKey;
    private final String model;
    private final String taskType;
    private final int dimension;
    private final ChatProperties.A2 settings;
    private final Clock clock;
    private final Transport transport;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, double[]> cache;

    // Trạng thái bảo vệ hạn mức; mọi truy cập trong synchronized(this).
    private LocalDate counterDate;
    private int callsToday;
    private boolean capWarned;
    private Instant pausedUntil = Instant.MIN;
    private int consecutiveFailures;
    private Instant circuitOpenUntil = Instant.MIN;

    public GeminiEmbeddingClient(boolean hasKey, String model, String taskType, int dimension,
                                 ChatProperties.A2 settings, Clock clock, Transport transport) {
        this.hasKey = hasKey;
        this.model = model;
        this.taskType = taskType;
        this.dimension = dimension;
        this.settings = settings;
        this.clock = clock;
        this.transport = transport;
        int capacity = Math.max(0, settings.cacheSize());
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, double[]> eldest) {
                return size() > capacity;
            }
        };
    }

    /** Client thật: RestClient với timeout ngắn, key trong header x-goog-api-key. */
    public static GeminiEmbeddingClient create(ChatProperties.Gemini gemini, ChatProperties.A2 settings,
                                               String model, String taskType, int dimension, Clock clock) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(settings.connectTimeoutMs());
        factory.setReadTimeout(settings.timeoutMs());
        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(gemini.baseUrl())
                .build();
        String apiKey = gemini.apiKey();
        Transport transport = (embeddingModel, body) -> restClient.post()
                .uri("/models/{model}:embedContent", embeddingModel)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return new GeminiEmbeddingClient(gemini.enabled(), model, taskType, dimension, settings, clock, transport);
    }

    public String model() {
        return model;
    }

    /** Thân yêu cầu đúng hợp đồng MODEL_FORMAT.md mục A2. */
    JsonNode requestBody(String text) {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("content").putArray("parts").addObject().put("text", text);
        body.put("taskType", taskType);
        body.put("outputDimensionality", dimension);
        return body;
    }

    /** Khoá cache: NFC, chữ thường, gộp khoảng trắng (giữ dấu). */
    static String cacheKey(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }

    /**
     * Có thể gọi API ngay lúc này không (có key, không bị tạm dừng / ngắt mạch, còn trần ngày). Không tính cache.
     */
    public synchronized boolean isAvailable() {
        return hasKey && blockedStatus(clock.instant()) == null;
    }

    /** Lấy embedding; không bao giờ ném lỗi. */
    public Result embed(String text) {
        if (text == null || text.isBlank()) {
            return Result.of(Status.EMPTY_TEXT);
        }
        String key = cacheKey(text);
        synchronized (this) {
            double[] cached = cache.get(key);
            if (cached != null) {
                return new Result(Status.CACHED, cached);
            }
            if (!hasKey) {
                return Result.of(Status.NO_API_KEY);
            }
            Status blocked = blockedStatus(clock.instant());
            if (blocked != null) {
                return Result.of(blocked);
            }
            callsToday++; // giữ chỗ trước khi gọi để nhiều luồng không vượt trần
        }

        JsonNode response;
        try {
            response = transport.post(model, requestBody(text));
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429) {
                onRateLimited(isDailyQuota(e));
                return Result.of(Status.RATE_LIMITED);
            }
            onFailure("HTTP " + status);
            return Result.of(Status.FAILED);
        } catch (RuntimeException e) {
            onFailure(e.getClass().getSimpleName());
            return Result.of(Status.FAILED);
        }

        double[] values = parse(response);
        if (values == null) {
            onFailure("phản hồi không có embedding " + dimension + " chiều");
            return Result.of(Status.FAILED);
        }
        synchronized (this) {
            consecutiveFailures = 0;
            if (settings.cacheSize() > 0) {
                cache.put(key, values);
            }
        }
        return new Result(Status.OK, values);
    }

    private double[] parse(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode values = response.path("embedding").path("values");
        if (!values.isArray() || values.size() != dimension) {
            return null;
        }
        double[] vector = new double[dimension];
        for (int i = 0; i < dimension; i++) {
            JsonNode value = values.get(i);
            if (!value.isNumber()) {
                return null;
            }
            vector[i] = value.asDouble();
        }
        return vector;
    }

    /** null = được gọi; gọi trong synchronized. Tự đặt lại bộ đếm khi sang ngày mới (giờ Việt Nam). */
    private Status blockedStatus(Instant now) {
        if (now.isBefore(pausedUntil)) {
            return Status.RATE_LIMITED;
        }
        if (now.isBefore(circuitOpenUntil)) {
            return Status.CIRCUIT_OPEN;
        }
        LocalDate today = now.atZone(AppConfig.CLINIC_ZONE).toLocalDate();
        if (!today.equals(counterDate)) {
            counterDate = today;
            callsToday = 0;
            capWarned = false;
        }
        if (callsToday >= settings.dailyCap()) {
            if (!capWarned) {
                capWarned = true;
                log.warn("A2: đã dùng hết trần {} lần gọi embedContent hôm nay, bỏ qua A2 tới ngày mai",
                        settings.dailyCap());
            }
            return Status.DAILY_CAP;
        }
        return null;
    }

    private static boolean isDailyQuota(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        String lower = body == null ? "" : body.toLowerCase(Locale.ROOT);
        return lower.contains("perday") || lower.contains("per day");
    }

    private synchronized void onRateLimited(boolean daily) {
        Instant now = clock.instant();
        Instant until = daily
                ? now.atZone(QUOTA_RESET_ZONE).toLocalDate().plusDays(1).atStartOfDay(QUOTA_RESET_ZONE).toInstant()
                : now.plus(Duration.ofMinutes(settings.rateLimitPauseMinutes()));
        if (until.isAfter(pausedUntil)) {
            pausedUntil = until;
        }
        log.warn("A2: Gemini embedContent hết hạn mức (429{}), tạm tắt A2 tới {}",
                daily ? ", theo ngày" : "", pausedUntil);
    }

    private synchronized void onFailure(String reason) {
        consecutiveFailures++;
        if (consecutiveFailures >= settings.failureThreshold()) {
            circuitOpenUntil = clock.instant().plusSeconds(settings.circuitOpenSeconds());
            consecutiveFailures = 0;
            log.warn("A2: embedContent lỗi {} lần liên tiếp (lần cuối: {}), ngắt mạch {} giây",
                    settings.failureThreshold(), reason, settings.circuitOpenSeconds());
        } else {
            log.debug("A2: embedContent lỗi: {}", reason);
        }
    }

    /** Số lần đã gọi API hôm nay (cho test / giám sát). */
    public synchronized int callsToday() {
        return callsToday;
    }
}
