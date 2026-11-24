package com.vetclinic.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.chat")
public record ChatProperties(
        @DefaultValue("20") int maxHistoryMessages,
        @DefaultValue("1000") int maxMessageChars,
        @DefaultValue("4") int maxToolRounds,
        /** Tổng thời gian tối đa cho một câu trả lời (cả các vòng công cụ); quá hạn thì trả câu dự phòng. */
        @DefaultValue("45") int replyTimeoutSeconds,
        @DefaultValue Gemini gemini,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue IntentSettings intent
) {

    /** Cách nhận diện ý định: chỉ luật, chỉ ML, hay kết hợp (mặc định). */
    public enum IntentMode {
        RULES, ML, HYBRID
    }

    /**
     * Bộ phân loại ý định (CompositeIntentClassifier).
     *
     * @param mode      rules | ml | hybrid
     * @param modelPath file JSON của mô hình ML (Spring resource: classpath:..., file:...)
     * @param threshold ghi đè ngưỡng tin cậy trong file mô hình; null = dùng ngưỡng trong file
     * @param a2        tầng phụ A2 (embedding Gemini + hồi quy logistic), chỉ gọi khi A1 không chắc
     */
    public record IntentSettings(
            @DefaultValue("hybrid") IntentMode mode,
            @DefaultValue("classpath:ml/intent-model.json") String modelPath,
            Double threshold,
            @DefaultValue A2 a2
    ) {
    }

    /**
     * Tầng phụ A2 của bộ phân loại ý định (xem CompositeIntentClassifier, ml/chat-intent/MODEL_FORMAT.md mục A2).
     * Dùng chung GEMINI_API_KEY và base-url với app.chat.gemini; tên model embedding lấy từ file mô hình.
     *
     * @param enabled               bật/tắt; tự tắt khi không có API key hoặc không nạp được file mô hình
     * @param modelPath             file JSON A2 (Spring resource)
     * @param triggerBelow          chỉ gọi A2 khi confidence của A1 nhỏ hơn giá trị này
     * @param minWords              câu (đã chuẩn hoá) ít hơn chừng này từ thì không gọi A2
     * @param dailyCap              trần số lần gọi embedContent mỗi ngày (giờ Việt Nam)
     * @param connectTimeoutMs      timeout kết nối tới Gemini
     * @param timeoutMs             timeout đọc phản hồi
     * @param cacheSize             số câu nhớ embedding (LRU); 0 = không cache
     * @param rateLimitPauseMinutes gặp HTTP 429 (hạn mức theo phút) thì tạm tắt A2 chừng này phút
     * @param failureThreshold      số lỗi 5xx/timeout liên tiếp thì ngắt mạch
     * @param circuitOpenSeconds    thời gian ngắt mạch
     */
    public record A2(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("classpath:ml/intent-model-a2.json") String modelPath,
            @DefaultValue("0.6") double triggerBelow,
            @DefaultValue("3") int minWords,
            @DefaultValue("900") int dailyCap,
            @DefaultValue("500") int connectTimeoutMs,
            @DefaultValue("1200") int timeoutMs,
            @DefaultValue("2000") int cacheSize,
            @DefaultValue("15") int rateLimitPauseMinutes,
            @DefaultValue("3") int failureThreshold,
            @DefaultValue("60") int circuitOpenSeconds
    ) {

        /** A2 tắt — dùng trong test và khi cần dựng IntentSettings bằng tay. */
        public static A2 disabled() {
            return new A2(false, "", 0.6, 3, 900, 500, 1200, 2000, 15, 3, 60);
        }

        /** Giá trị mặc định (như application.yml), bật. */
        public static A2 defaults() {
            return new A2(true, "classpath:ml/intent-model-a2.json", 0.6, 3, 900, 500, 1200, 2000, 15, 3, 60);
        }
    }

    public record Gemini(
            @DefaultValue("") String apiKey,
            @DefaultValue("https://generativelanguage.googleapis.com/v1beta") String baseUrl,
            @DefaultValue("gemini-3.5-flash-lite") String model,
            @DefaultValue List<String> fallbackModels,
            /** Thời gian chờ đọc tối đa của MỘT lần gọi model. */
            @DefaultValue("15") int timeoutSeconds,
            /** Chưa có kết quả sau chừng này giây thì chạy thêm model dự phòng song song. */
            @DefaultValue("3") int hedgeSeconds,
            /** Hạn chót cho một lượt generateContent (gồm mọi model thử). */
            @DefaultValue("25") int totalTimeoutSeconds
    ) {

        /** Chưa có API key thì chatbot tắt hẳn. */
        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }

        /** Model chính trước, rồi các model dự phòng (bỏ trùng, bỏ rỗng). */
        public List<String> allModels() {
            List<String> models = new ArrayList<>();
            if (model != null && !model.isBlank()) {
                models.add(model.trim());
            }
            for (String fallback : fallbackModels) {
                if (fallback != null && !fallback.isBlank() && !models.contains(fallback.trim())) {
                    models.add(fallback.trim());
                }
            }
            return models;
        }
    }

    public record RateLimit(
            @DefaultValue("10") int windowMinutes,
            @DefaultValue("20") int guestPerWindow,
            @DefaultValue("60") int userPerWindow,
            @DefaultValue("3000") int dailyCap
    ) {
    }
}
