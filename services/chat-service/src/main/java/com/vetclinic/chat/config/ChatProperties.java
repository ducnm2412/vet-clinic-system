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
        @DefaultValue RateLimit rateLimit
) {

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
