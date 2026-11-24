package com.vetclinic.chat.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Mô hình dựng sẵn cho test. */
final class TestModels {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private TestModels() {
    }

    /** Mô hình viết tay ở src/test/resources/ml/tiny-intent-model.json (OTHER, PRICE, EMERGENCY). */
    static MlIntentModel tiny() {
        try (InputStream in = TestModels.class.getResourceAsStream("/ml/tiny-intent-model.json")) {
            return MlIntentModel.load(in, MAPPER);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Mô hình "bất kỳ" không có đặc trưng nào: câu nào cũng ra {@code label} với đúng {@code confidence}
     * (logit = ln p, softmax trả lại p); phần còn lại chia đều cho hai nhãn khác. Dùng để giả lập mô hình
     * tự tin sai. Cần confidence > 1/3 để {@code label} là argmax.
     */
    static MlIntentModel constant(Intent label, double confidence, double threshold) {
        Intent[] others = label == Intent.OTHER
                ? new Intent[]{Intent.GREETING, Intent.THANKS}
                : label == Intent.GREETING ? new Intent[]{Intent.OTHER, Intent.THANKS}
                : new Intent[]{Intent.OTHER, Intent.GREETING};
        double rest = Math.log((1 - confidence) / 2);
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", 1);
        root.put("kind", "tfidf-logreg");
        root.putArray("labels").add(label.name()).add(others[0].name()).add(others[1].name());
        root.put("threshold", threshold);
        ObjectNode block = root.putArray("blocks").addObject();
        block.put("name", "word");
        block.put("offset", 0);
        block.putObject("vocab");
        block.putArray("idf");
        root.putArray("coef").add(MAPPER.createArrayNode()).add(MAPPER.createArrayNode()).add(MAPPER.createArrayNode());
        root.putArray("intercept").add(Math.log(confidence)).add(rest).add(rest);
        return MlIntentModel.fromJson(root);
    }

    static MlIntentClassifier constantClassifier(Intent label, double confidence) {
        return new MlIntentClassifier(constant(label, confidence, 0.5), null);
    }
}
