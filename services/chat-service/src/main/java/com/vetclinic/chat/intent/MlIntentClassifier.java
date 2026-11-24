package com.vetclinic.chat.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Bộ phân loại ML (TF-IDF + hồi quy logistic, xem {@link MlIntentModel}). Không phải bean riêng:
 * CompositeIntentClassifier nạp nó từ app.chat.intent.model-path và quyết định khi nào tin nhãn ML.
 *
 * {@link #classify} trả nhãn ML khi confidence ≥ ngưỡng, ngược lại OTHER.
 */
public class MlIntentClassifier implements IntentClassifier {

    private final MlIntentModel model;
    private final double threshold;

    /** @param thresholdOverride null = dùng ngưỡng ghi trong file mô hình */
    public MlIntentClassifier(MlIntentModel model, Double thresholdOverride) {
        this.model = model;
        this.threshold = thresholdOverride != null ? thresholdOverride : model.threshold();
    }

    /**
     * Nạp mô hình từ resource.
     *
     * @throws IOException                         không có file / không đọc được / JSON hỏng
     * @throws MlIntentModel.InvalidModelException JSON đọc được nhưng sai hợp đồng (vd. nhãn lạ)
     */
    public static MlIntentClassifier load(Resource resource, ObjectMapper mapper, Double thresholdOverride)
            throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return new MlIntentClassifier(MlIntentModel.load(in, mapper), thresholdOverride);
        }
    }

    public MlIntentModel.Prediction predict(String message) {
        return model.predict(message);
    }

    public double threshold() {
        return threshold;
    }

    public boolean isConfident(MlIntentModel.Prediction prediction) {
        return prediction.confidence() >= threshold;
    }

    @Override
    public Intent classify(String message) {
        MlIntentModel.Prediction prediction = predict(message);
        return isConfident(prediction) ? prediction.intent() : Intent.OTHER;
    }
}
