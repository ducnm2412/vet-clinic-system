package com.vetclinic.chat.intent;

import com.vetclinic.chat.gemini.GeminiEmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Tầng phụ A2: lấy embedding câu từ Gemini ({@link GeminiEmbeddingClient}) rồi phân loại bằng
 * {@link EmbeddingIntentModel}. Không phải bean riêng: CompositeIntentClassifier tạo nó và chỉ gọi khi A1 không chắc.
 *
 * {@link #predict} không bao giờ ném lỗi: bỏ qua / lỗi / hết hạn mức → {@link Optional#empty()} để tầng trên dùng
 * logic cũ.
 */
public class EmbeddingIntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingIntentClassifier.class);

    private final EmbeddingIntentModel model;
    private final GeminiEmbeddingClient client;
    private final int minWords;

    /** @param minWords câu đã chuẩn hoá ít hơn chừng này từ thì không gọi A2 */
    public EmbeddingIntentClassifier(EmbeddingIntentModel model, GeminiEmbeddingClient client, int minWords) {
        this.model = model;
        this.client = client;
        this.minWords = minWords;
    }

    public EmbeddingIntentModel model() {
        return model;
    }

    public double threshold() {
        return model.threshold();
    }

    public boolean isConfident(MlIntentModel.Prediction prediction) {
        return model.isConfident(prediction);
    }

    /** Câu có đủ điều kiện để gọi A2 không (không rỗng sau chuẩn hoá, đủ số từ). */
    public boolean accepts(String message) {
        String normalized = TextNormalizer.normalize(message);
        return !normalized.isEmpty() && normalized.split(" ").length >= minWords;
    }

    public Optional<MlIntentModel.Prediction> predict(String message) {
        try {
            if (!accepts(message)) {
                return Optional.empty();
            }
            // Gửi văn bản gốc (đã cắt khoảng trắng hai đầu) như lúc huấn luyện, không gửi bản bỏ dấu.
            GeminiEmbeddingClient.Result result = client.embed(message.trim());
            if (!result.hasVector()) {
                log.debug("A2 bỏ qua: {}", result.status());
                return Optional.empty();
            }
            return Optional.of(model.predict(result.values()));
        } catch (RuntimeException e) {
            log.warn("A2 lỗi khi phân loại, dùng logic cũ: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
