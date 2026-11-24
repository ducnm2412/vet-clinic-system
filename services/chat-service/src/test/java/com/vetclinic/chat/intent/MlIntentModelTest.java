package com.vetclinic.chat.intent;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Đối chiếu MlIntentModel với phép tính tay trên mô hình viết tay tiny-intent-model.json:
 * labels [OTHER, PRICE, EMERGENCY]; word vocab {gia, bao nhieu, co giat} idf [1, 2, 1.5];
 * char (2..3) vocab {" g", "ia", " gi"} idf [1, 3, 0.5].
 */
class MlIntentModelTest {

    private static final double[][] COEF = {
            {0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
            {2.0, 1.5, -1.0, 0.5, 0.2, 0.1},
            {-0.5, 0.0, 3.0, 0.1, 0.3, 0.0}
    };
    private static final double[] INTERCEPT = {0.5, -0.2, -0.4};

    private final MlIntentModel model = TestModels.tiny();

    /** Khối char khi câu có đúng một từ chứa " g", "ia", " gi" (mỗi cái một lần) — chỉ hướng [1, 3, 0.5]. */
    private static double[] charGia() {
        double norm = Math.sqrt(1 + 9 + 0.25);
        return new double[]{1 / norm, 3 / norm, 0.5 / norm};
    }

    private static double[] expectedProbs(double[] word, double[] chars) {
        double[] x = {word[0], word[1], word[2], chars[0], chars[1], chars[2]};
        double[] logits = new double[3];
        for (int k = 0; k < 3; k++) {
            logits[k] = INTERCEPT[k];
            for (int j = 0; j < x.length; j++) {
                logits[k] += COEF[k][j] * x[j];
            }
        }
        double max = Math.max(logits[0], Math.max(logits[1], logits[2]));
        double sum = 0;
        double[] p = new double[3];
        for (int k = 0; k < 3; k++) {
            p[k] = Math.exp(logits[k] - max);
            sum += p[k];
        }
        for (int k = 0; k < 3; k++) {
            p[k] /= sum;
        }
        return p;
    }

    private static void assertProbs(MlIntentModel.Prediction prediction, double[] expected) {
        assertThat(prediction.probs()).hasSize(expected.length);
        for (int k = 0; k < expected.length; k++) {
            assertThat(prediction.probs()[k]).as("p[%d]", k).isCloseTo(expected[k], within(1e-12));
        }
    }

    @Test
    void parsesLabelsThresholdAndFeatureCount() {
        assertThat(model.labels()).containsExactly(Intent.OTHER, Intent.PRICE, Intent.EMERGENCY);
        assertThat(model.threshold()).isEqualTo(0.5);
        assertThat(model.featureCount()).isEqualTo(6);
    }

    @Test
    void unigramAndBigramWithDiacriticsAndPunctuation() {
        // "gia bao nhieu": word gia=1, "bao nhieu"=1 → [1·1, 1·2, 0] / √5
        double[] word = {1 / Math.sqrt(5), 2 / Math.sqrt(5), 0};

        MlIntentModel.Prediction prediction = model.predict("Giá bao nhiêu?");

        double[] expected = expectedProbs(word, charGia());
        assertProbs(prediction, expected);
        assertThat(prediction.intent()).isEqualTo(Intent.PRICE);
        assertThat(prediction.confidence()).isCloseTo(expected[1], within(1e-12));
    }

    @Test
    void termFrequencyIsSublinear() {
        // gia xuất hiện 2 lần → 1 + ln 2; khối char: mọi n-gram ×2 → cùng hướng [1, 3, 0.5].
        double gia = 1 + Math.log(2);
        double norm = Math.sqrt(gia * gia + 4);
        double[] word = {gia / norm, 2 / norm, 0};

        assertProbs(model.predict("gia gia bao nhieu"), expectedProbs(word, charGia()));
    }

    @Test
    void bigramOnlyMessage() {
        // "meo co giat": word chỉ có bigram "co giat" → [0, 0, 1]; char từ "giat" → " g", "ia", " gi".
        MlIntentModel.Prediction prediction = model.predict("mèo co giật");

        double[] expected = expectedProbs(new double[]{0, 0, 1}, charGia());
        assertProbs(prediction, expected);
        assertThat(prediction.intent()).isEqualTo(Intent.EMERGENCY);
    }

    @Test
    void unknownTermsGiveInterceptOnly() {
        MlIntentModel.Prediction prediction = model.predict("xyz");

        assertProbs(prediction, expectedProbs(new double[3], new double[3]));
        assertThat(prediction.intent()).isEqualTo(Intent.OTHER);
    }

    @Test
    void emptyMessageIsOtherWithZeroConfidence() {
        for (String empty : new String[]{null, "", "   ", "?!..."}) {
            MlIntentModel.Prediction prediction = model.predict(empty);
            assertThat(prediction.intent()).isEqualTo(Intent.OTHER);
            assertThat(prediction.confidence()).isZero();
        }
    }

    @Test
    void unknownLabelFailsWithClearMessage() throws Exception {
        ObjectNode root = tinyJson();
        ((ArrayNode) root.get("labels")).set(1, root.textNode("REFUND"));

        assertThatThrownBy(() -> MlIntentModel.fromJson(root))
                .isInstanceOf(MlIntentModel.InvalidModelException.class)
                .hasMessageContaining("REFUND")
                .hasMessageContaining("Intent");
    }

    @Test
    void dimensionMismatchIsRejected() throws Exception {
        ObjectNode root = tinyJson();
        ((ArrayNode) root.get("coef").get(0)).remove(0);

        assertThatThrownBy(() -> MlIntentModel.fromJson(root))
                .isInstanceOf(MlIntentModel.InvalidModelException.class)
                .hasMessageContaining("coef[0]");
    }

    @Test
    void wrongKindIsRejected() throws Exception {
        ObjectNode root = tinyJson();
        root.put("kind", "naive-bayes");

        assertThatThrownBy(() -> MlIntentModel.fromJson(root))
                .isInstanceOf(MlIntentModel.InvalidModelException.class)
                .hasMessageContaining("kind");
    }

    private static ObjectNode tinyJson() throws Exception {
        try (InputStream in = MlIntentModelTest.class.getResourceAsStream("/ml/tiny-intent-model.json")) {
            return (ObjectNode) TestModels.MAPPER.readTree(in);
        }
    }
}
