package com.vetclinic.chat.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetclinic.chat.config.ChatProperties;
import com.vetclinic.chat.config.ChatProperties.IntentMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;

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

    @Autowired
    public CompositeIntentClassifier(RuleIntentClassifier rules, ChatProperties properties,
                                     ResourceLoader resourceLoader, ObjectMapper mapper) {
        this(rules, properties.intent().mode(),
                properties.intent().mode() == IntentMode.RULES
                        ? null
                        : loadModel(resourceLoader, mapper, properties.intent()));
    }

    /** @param ml null = không có mô hình, mọi chế độ lùi về rules */
    public CompositeIntentClassifier(RuleIntentClassifier rules, IntentMode requestedMode, MlIntentClassifier ml) {
        this.rules = rules;
        this.ml = ml;
        IntentMode requested = requestedMode == null ? IntentMode.HYBRID : requestedMode;
        if (requested != IntentMode.RULES && ml == null) {
            log.warn("Không có mô hình ý định ML, chế độ {} lùi về rules", requested.name().toLowerCase());
            this.mode = IntentMode.RULES;
        } else {
            this.mode = requested;
        }
        log.info("Bộ phân loại ý định: chế độ {}{}", mode.name().toLowerCase(),
                ml != null && mode != IntentMode.RULES ? ", ngưỡng ML " + ml.threshold() : "");
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

    @Override
    public Intent classify(String message) {
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
