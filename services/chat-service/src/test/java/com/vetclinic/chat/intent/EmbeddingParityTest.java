package com.vetclinic.chat.intent;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Đối chiếu phần phân loại A2 của Java với Python: train_a2.py xuất intent-model-a2.json + parity-a2.json
 * (embedding đã chuẩn hoá lấy từ cache + xác suất do sklearn tính). Không gọi mạng.
 */
class EmbeddingParityTest {

    private static final double TOLERANCE = 1e-4;

    @Test
    void javaMatchesPythonOnParitySentences() throws Exception {
        EmbeddingIntentModel model;
        try (InputStream in = getClass().getResourceAsStream("/ml/intent-model-a2.json")) {
            assertThat(in).as("src/test/resources/ml/intent-model-a2.json").isNotNull();
            model = EmbeddingIntentModel.load(in, TestModels.MAPPER);
        }
        JsonNode cases;
        try (InputStream in = getClass().getResourceAsStream("/ml/parity-a2.json")) {
            assertThat(in).as("src/test/resources/ml/parity-a2.json").isNotNull();
            cases = TestModels.MAPPER.readTree(in);
        }
        assertThat(cases.isArray() && !cases.isEmpty()).as("parity-a2.json phải là mảng không rỗng").isTrue();

        for (JsonNode item : cases) {
            String text = item.path("text").asText("");
            JsonNode embeddingNode = item.path("embedding");
            double[] embedding = new double[embeddingNode.size()];
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] = embeddingNode.get(i).asDouble();
            }

            MlIntentModel.Prediction prediction = model.predict(embedding);

            assertThat(prediction.intent()).as("nhãn của \"%s\"", text)
                    .isEqualTo(Intent.valueOf(item.path("label").asText()));
            JsonNode probs = item.path("probs");
            assertThat(probs.size()).isEqualTo(model.labels().size());
            for (int k = 0; k < probs.size(); k++) {
                assertThat(prediction.probs()[k])
                        .as("p[%s] của \"%s\"", model.labels().get(k), text)
                        .isCloseTo(probs.get(k).asDouble(), within(TOLERANCE));
            }
        }
    }
}
