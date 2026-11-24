package com.vetclinic.chat.intent;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Đối chiếu Java với Python: train.py xuất intent-model.json + parity.json (xác suất do sklearn tính).
 * Chép cả hai vào src/test/resources/ml/ để bật test này; chưa có thì bỏ qua.
 */
class MlIntentParityTest {

    private static final double TOLERANCE = 1e-4;

    @Test
    void javaMatchesPythonOnParitySentences() throws Exception {
        URL modelUrl = getClass().getResource("/ml/intent-model.json");
        URL parityUrl = getClass().getResource("/ml/parity.json");
        Assumptions.assumeTrue(modelUrl != null && parityUrl != null,
                "Chưa có src/test/resources/ml/intent-model.json + parity.json — bỏ qua test đối chiếu");

        MlIntentModel model;
        try (InputStream in = modelUrl.openStream()) {
            model = MlIntentModel.load(in, TestModels.MAPPER);
        }
        JsonNode cases;
        try (InputStream in = parityUrl.openStream()) {
            cases = TestModels.MAPPER.readTree(in);
        }
        assertThat(cases.isArray() && !cases.isEmpty()).as("parity.json phải là mảng không rỗng").isTrue();

        for (JsonNode item : cases) {
            String text = item.path("text").asText("");
            Intent expected = Intent.valueOf(item.path("label").asText());
            MlIntentModel.Prediction prediction = model.predict(text);

            if (TextNormalizer.normalize(text).isEmpty()) {
                // Câu rỗng không tính đặc trưng: chỉ yêu cầu ra OTHER.
                assertThat(prediction.intent()).as("nhãn của %s", quote(text)).isEqualTo(Intent.OTHER);
                continue;
            }
            assertThat(prediction.intent()).as("nhãn của %s", quote(text)).isEqualTo(expected);
            JsonNode probs = item.path("probs");
            assertThat(probs.size()).as("số xác suất của %s", quote(text)).isEqualTo(model.labels().size());
            for (int k = 0; k < probs.size(); k++) {
                assertThat(prediction.probs()[k])
                        .as("p[%s] của %s", model.labels().get(k), quote(text))
                        .isCloseTo(probs.get(k).asDouble(), within(TOLERANCE));
            }
        }
    }

    private static String quote(String text) {
        return "\"" + text + "\"";
    }
}
