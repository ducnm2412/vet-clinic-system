package com.vetclinic.chat.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetclinic.chat.config.ChatProperties;
import com.vetclinic.chat.config.ChatProperties.IntentMode;
import com.vetclinic.chat.gemini.GeminiEmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Optional;

/**
 * Bộ phân loại ý định ChatService dùng, chọn theo app.chat.intent.mode:
 * <ul>
 *   <li>{@code rules}: chỉ RuleIntentClassifier.</li>
 *   <li>{@code hybrid} (mặc định): luật ra EMERGENCY → EMERGENCY ngay; ML đủ tin cậy → nhãn ML (kể cả
 *       EMERGENCY, để bắt câu nguy hiểm mà luật bỏ sót); còn lại → nhãn luật.</li>
 *   <li>{@code ml}: ML đủ tin cậy → nhãn ML, dưới ngưỡng → OTHER; luật EMERGENCY vẫn ghi đè.</li>
 * </ul>
 * Bất biến an toàn: câu luật nhận là EMERGENCY luôn ra EMERGENCY, ở mọi chế độ, với mọi mô hình. Câu luật
 * nhận là OFF_TOPIC (dò prompt, jailbreak, nội dung vi phạm) cũng ghi đè ML như vậy.
 *
 * Tầng phụ A2 (embedding Gemini + hồi quy logistic, app.chat.intent.a2.*), chỉ ở {@code hybrid} và {@code ml}:
 * sau khi luật không ghi đè, A1 dự đoán; CHỈ khi confidence A1 &lt; {@code a2.trigger-below} mới gọi A2. A2 đủ tin
 * cậy (≥ ngưỡng trong file A2) → nhãn A2 (kể cả EMERGENCY); A2 không chắc / lỗi / bị bỏ qua (hết trần, 429, ngắt
 * mạch, timeout, câu quá ngắn) → logic cũ. A2 không bao giờ ném lỗi ra ngoài. Không có API key hoặc không nạp được
 * file A2 (kể cả sai hợp đồng — A2 chỉ là tầng phụ) → cảnh báo và chạy như chưa có A2.
 *
 * Không có file mô hình hoặc không đọc được → cảnh báo và lùi về {@code rules} (service vẫn chạy).
 * File đọc được nhưng sai hợp đồng (nhãn lạ, sai kích thước) → lỗi khởi động, vì đó là lỗi triển khai.
 * Không ghi log nội dung tin nhắn; chỉ ghi nhãn + confidence ở mức DEBUG.
 */
@Primary
@Component
public class CompositeIntentClassifier implements IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(CompositeIntentClassifier.class);

    private final RuleIntentClassifier rules;
    private final MlIntentClassifier ml;
    private final IntentMode mode;
    private final EmbeddingIntentClassifier a2;
    private final double a2TriggerBelow;

    @Autowired
    public CompositeIntentClassifier(RuleIntentClassifier rules, ChatProperties properties,
                                     ResourceLoader resourceLoader, ObjectMapper mapper, Clock clock) {
        this(rules, properties.intent().mode(),
                properties.intent().mode() == IntentMode.RULES
                        ? null
                        : loadModel(resourceLoader, mapper, properties.intent()),
                properties.intent().mode() == IntentMode.RULES
                        ? null
                        : loadA2(resourceLoader, mapper, properties, clock),
                properties.intent().a2() == null ? 0 : properties.intent().a2().triggerBelow());
    }

    /** @param ml null = không có mô hình, mọi chế độ lùi về rules */
    public CompositeIntentClassifier(RuleIntentClassifier rules, IntentMode requestedMode, MlIntentClassifier ml) {
        this(rules, requestedMode, ml, null, 0);
    }

    /**
     * @param a2             null = không có tầng A2
     * @param a2TriggerBelow gọi A2 khi confidence A1 nhỏ hơn giá trị này
     */
    public CompositeIntentClassifier(RuleIntentClassifier rules, IntentMode requestedMode, MlIntentClassifier ml,
                                     EmbeddingIntentClassifier a2, double a2TriggerBelow) {
        this.rules = rules;
        this.ml = ml;
        IntentMode requested = requestedMode == null ? IntentMode.HYBRID : requestedMode;
        if (requested != IntentMode.RULES && ml == null) {
            log.warn("Không có mô hình ý định ML, chế độ {} lùi về rules", requested.name().toLowerCase());
            this.mode = IntentMode.RULES;
        } else {
            this.mode = requested;
        }
        // A2 chỉ là tầng sau A1: không có A1 (đã lùi về rules) thì cũng không dùng A2.
        this.a2 = mode == IntentMode.RULES ? null : a2;
        this.a2TriggerBelow = a2TriggerBelow;
        log.info("Bộ phân loại ý định: chế độ {}{}{}", mode.name().toLowerCase(),
                ml != null && mode != IntentMode.RULES ? ", ngưỡng ML " + ml.threshold() : "",
                this.a2 != null ? ", A2 " + this.a2.model().embeddingModel() + " khi ML < " + a2TriggerBelow
                        + " (ngưỡng A2 " + this.a2.threshold() + ")" : "");
    }

    /**
     * Nạp tầng A2; trả null (kèm cảnh báo) khi bị tắt, chưa có API key, hoặc không nạp được file mô hình A2
     * (kể cả file sai hợp đồng: A2 là tầng phụ, không được làm service dừng).
     */
    static EmbeddingIntentClassifier loadA2(ResourceLoader resourceLoader, ObjectMapper mapper,
                                            ChatProperties properties, Clock clock) {
        ChatProperties.A2 settings = properties.intent().a2();
        if (settings == null || !settings.enabled()) {
            log.info("A2 (embedding Gemini) đang tắt");
            return null;
        }
        if (properties.gemini() == null || !properties.gemini().enabled()) {
            log.warn("A2 (embedding Gemini) tự tắt: chưa có GEMINI_API_KEY");
            return null;
        }
        String path = settings.modelPath();
        if (path == null || path.isBlank()) {
            log.warn("A2 tự tắt: chưa cấu hình app.chat.intent.a2.model-path");
            return null;
        }
        Resource resource = resourceLoader.getResource(path.trim());
        if (!resource.exists()) {
            log.warn("A2 tự tắt: không tìm thấy mô hình A2 tại {}", path);
            return null;
        }
        EmbeddingIntentModel model;
        try (InputStream in = resource.getInputStream()) {
            model = EmbeddingIntentModel.load(in, mapper);
        } catch (IOException | RuntimeException e) {
            log.warn("A2 tự tắt: không nạp được mô hình A2 tại {}: {}", path, e.getMessage());
            return null;
        }
        GeminiEmbeddingClient client = GeminiEmbeddingClient.create(properties.gemini(), settings,
                model.embeddingModel(), model.taskType(), model.dimension(), clock);
        log.info("Đã nạp mô hình A2 từ {}", path);
        return new EmbeddingIntentClassifier(model, client, settings.minWords());
    }

    /**
     * Nạp mô hình; trả null (kèm cảnh báo) nếu không có file hoặc không đọc được.
     *
     * @throws MlIntentModel.InvalidModelException file sai hợp đồng — để service dừng với lỗi rõ ràng
     */
    static MlIntentClassifier loadModel(ResourceLoader resourceLoader, ObjectMapper mapper,
                                        ChatProperties.IntentSettings settings) {
        String path = settings.modelPath();
        if (path == null || path.isBlank()) {
            log.warn("Chưa cấu hình app.chat.intent.model-path");
            return null;
        }
        Resource resource = resourceLoader.getResource(path.trim());
        if (!resource.exists()) {
            log.warn("Không tìm thấy mô hình ý định tại {}", path);
            return null;
        }
        try {
            MlIntentClassifier classifier = MlIntentClassifier.load(resource, mapper, settings.threshold());
            log.info("Đã nạp mô hình ý định từ {}", path);
            return classifier;
        } catch (IOException e) {
            log.warn("Không đọc được mô hình ý định tại {}: {}", path, e.getMessage());
            return null;
        }
    }

    /** Chế độ đang chạy thật (sau khi lùi về rules nếu thiếu mô hình). */
    public IntentMode effectiveMode() {
        return mode;
    }

    /** Có tầng A2 đang hoạt động không. */
    public boolean hasA2() {
        return a2 != null;
    }

    @Override
    public Intent classify(String message) {
        return classify(message, true);
    }

    @Override
    public Intent classify(String message, boolean allowRemote) {
        Intent ruleIntent = rules.classify(message);
        if (mode == IntentMode.RULES) {
            return ruleIntent;
        }
        // Luật khẩn cấp và luật chặn dò prompt/nội dung vi phạm là regex chính xác: luôn ghi đè ML.
        if (ruleIntent == Intent.EMERGENCY || ruleIntent == Intent.OFF_TOPIC) {
            log.debug("intent={} source=rules-override", ruleIntent);
            return ruleIntent;
        }

        MlIntentModel.Prediction prediction;
        try {
            prediction = ml.predict(message);
        } catch (RuntimeException e) {
            log.warn("Mô hình ý định lỗi khi dự đoán, dùng nhãn luật: {}", e.toString());
            return ruleIntent;
        }
        if (allowRemote && a2 != null && prediction.confidence() < a2TriggerBelow) {
            Optional<MlIntentModel.Prediction> second = a2.predict(message);
            if (second.isPresent() && a2.isConfident(second.get())) {
                if (log.isDebugEnabled()) {
                    log.debug("intent={} source=a2 confidence={} ml={} mlConfidence={} rule={}", second.get().intent(),
                            String.format("%.3f", second.get().confidence()), prediction.intent(),
                            String.format("%.3f", prediction.confidence()), ruleIntent);
                }
                return second.get().intent();
            }
        }
        boolean confident = ml.isConfident(prediction);
        Intent result;
        if (confident) {
            result = prediction.intent();
        } else {
            result = mode == IntentMode.ML ? Intent.OTHER : ruleIntent;
        }
        if (log.isDebugEnabled()) {
            log.debug("intent={} source={} ml={} confidence={} rule={}", result,
                    confident ? "ml" : (mode == IntentMode.ML ? "ml-below-threshold" : "rules"),
                    prediction.intent(), String.format("%.3f", prediction.confidence()), ruleIntent);
        }
        return result;
    }
}
