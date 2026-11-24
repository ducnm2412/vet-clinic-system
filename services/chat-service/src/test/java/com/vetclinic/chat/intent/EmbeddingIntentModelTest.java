package com.vetclinic.chat.intent;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class EmbeddingIntentModelTest {

    /** Logreg 3 chiều, 3 nhãn: mỗi nhãn "thích" một trục. */
    static ObjectNode logregJson() {
        ObjectNode root = TestModels.MAPPER.createObjectNode();
        root.put("version", 1);
        root.put("kind", "embedding-logreg");
        ObjectNode embedding = root.putObject("embedding");
        embedding.put("provider", "gemini");
        embedding.put("model", "gemini-embedding-2");
        embedding.put("endpoint", "embedContent");
        embedding.put("task_type", "CLASSIFICATION");
        embedding.put("output_dimensionality", 3);
        embedding.put("normalize", "l2");
        root.putArray("labels").add("PRICE").add("EMERGENCY").add("OTHER");
        root.put("threshold", 0.4);
        ArrayNode coef = root.putArray("coef");
        coef.addArray().add(4.0).add(0.0).add(0.0);
        coef.addArray().add(0.0).add(4.0).add(0.0);
        coef.addArray().add(0.0).add(0.0).add(4.0);
        root.putArray("intercept").add(0.0).add(0.0).add(0.5);
        return root;
    }

    static EmbeddingIntentModel tinyLogreg() {
        return EmbeddingIntentModel.fromJson(logregJson());
    }

    @Test
    void readsMetadata() {
        EmbeddingIntentModel model = tinyLogreg();

        assertThat(model.kind()).isEqualTo("embedding-logreg");
        assertThat(model.labels()).containsExactly(Intent.PRICE, Intent.EMERGENCY, Intent.OTHER);
        assertThat(model.threshold()).isEqualTo(0.4);
        assertThat(model.embeddingModel()).isEqualTo("gemini-embedding-2");
        assertThat(model.taskType()).isEqualTo("CLASSIFICATION");
        assertThat(model.dimension()).isEqualTo(3);
    }

    @Test
    void predictsWithSoftmaxOverLogits() {
        MlIntentModel.Prediction prediction = tinyLogreg().predict(new double[]{1, 0, 0});

        // logit = [4, 0, 0.5] → softmax
        double z = Math.exp(4) + Math.exp(0) + Math.exp(0.5);
        assertThat(prediction.intent()).isEqualTo(Intent.PRICE);
        assertThat(prediction.confidence()).isCloseTo(Math.exp(4) / z, within(1e-12));
        assertThat(Arrays.stream(prediction.probs()).sum()).isCloseTo(1.0, within(1e-12));
    }

    @Test
    void normalizesL2BeforeClassifying() {
        EmbeddingIntentModel model = tinyLogreg();
        double[] raw = {0, 30, 40};
        double[] unit = {0, 0.6, 0.8};

        assertThat(model.predict(raw).probs()).containsExactly(model.predict(unit).probs(), within(1e-12));
        assertThat(model.predict(raw).intent()).isEqualTo(Intent.OTHER);
    }

    @Test
    void zeroVectorIsKeptAsIs() {
        MlIntentModel.Prediction prediction = tinyLogreg().predict(new double[]{0, 0, 0});

        assertThat(prediction.intent()).isEqualTo(Intent.OTHER); // chỉ còn intercept
    }

    @Test
    void wrongVectorSizeIsRejected() {
        assertThatThrownBy(() -> tinyLogreg().predict(new double[]{1, 2}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3");
    }

    @Test
    void unknownLabelIsRejected() {
        ObjectNode json = logregJson();
        ((ArrayNode) json.get("labels")).set(0, TestModels.MAPPER.getNodeFactory().textNode("REFUND"));

        assertThatThrownBy(() -> EmbeddingIntentModel.fromJson(json))
                .isInstanceOf(MlIntentModel.InvalidModelException.class)
                .hasMessageContaining("REFUND");
    }

    @Test
    void coefWidthMustMatchOutputDimensionality() {
        ObjectNode json = logregJson();
        ((ObjectNode) json.get("embedding")).put("output_dimensionality", 4);

        assertThatThrownBy(() -> EmbeddingIntentModel.fromJson(json))
                .isInstanceOf(MlIntentModel.InvalidModelException.class)
                .hasMessageContaining("mong đợi 4");
    }

    @Test
    void coefRowsMustMatchLabels() {
        ObjectNode json = logregJson();
        ((ArrayNode) json.get("coef")).remove(2);

        assertThatThrownBy(() -> EmbeddingIntentModel.fromJson(json))
                .isInstanceOf(MlIntentModel.InvalidModelException.class);
    }

    @Test
    void unknownKindIsRejected() {
        ObjectNode json = logregJson();
        json.put("kind", "tfidf-logreg");

        assertThatThrownBy(() -> EmbeddingIntentModel.fromJson(json))
                .isInstanceOf(MlIntentModel.InvalidModelException.class)
                .hasMessageContaining("kind");
    }

    @Test
    void missingEmbeddingBlockIsRejected() {
        ObjectNode json = logregJson();
        json.remove("embedding");

        assertThatThrownBy(() -> EmbeddingIntentModel.fromJson(json))
                .isInstanceOf(MlIntentModel.InvalidModelException.class)
                .hasMessageContaining("embedding");
    }

    // --- MLP ---

    static ObjectNode mlpJson(String hiddenActivation) {
        ObjectNode root = logregJson();
        root.put("kind", "embedding-mlp");
        root.remove("coef");
        root.remove("intercept");
        ArrayNode layers = root.putArray("layers");
        ObjectNode hidden = layers.addObject();
        // 2 nơ-ron ẩn × 3 đầu vào
        ArrayNode w1 = hidden.putArray("weights");
        w1.addArray().add(1.0).add(-1.0).add(0.0);
        w1.addArray().add(0.0).add(1.0).add(-1.0);
        hidden.putArray("bias").add(0.0).add(0.1);
        hidden.put("activation", hiddenActivation);
        ObjectNode out = layers.addObject();
        // 3 nhãn × 2 nơ-ron ẩn
        ArrayNode w2 = out.putArray("weights");
        w2.addArray().add(2.0).add(0.0);
        w2.addArray().add(0.0).add(2.0);
        w2.addArray().add(-1.0).add(-1.0);
        out.putArray("bias").add(0.0).add(0.0).add(0.2);
        out.put("activation", "softmax");
        return root;
    }

    @Test
    void mlpMatchesHandComputation() {
        EmbeddingIntentModel model = EmbeddingIntentModel.fromJson(mlpJson("relu"));
        double[] x = {0.6, 0.0, 0.8}; // đã chuẩn hoá L2

        // h1 = relu([0.6, 0.1 - 0.8]) = [0.6, 0]; z2 = [1.2, 0, -0.6 + 0.2]
        double[] z = {1.2, 0.0, -0.4};
        double sum = Math.exp(z[0]) + Math.exp(z[1]) + Math.exp(z[2]);
        MlIntentModel.Prediction prediction = model.predict(x);

        assertThat(model.kind()).isEqualTo("embedding-mlp");
        assertThat(prediction.intent()).isEqualTo(Intent.PRICE);
        assertThat(prediction.probs()[0]).isCloseTo(Math.exp(z[0]) / sum, within(1e-12));
        assertThat(prediction.probs()[2]).isCloseTo(Math.exp(z[2]) / sum, within(1e-12));
    }

    @Test
    void mlpTanhActivation() {
        EmbeddingIntentModel model = EmbeddingIntentModel.fromJson(mlpJson("tanh"));
        double[] z = {2 * Math.tanh(0.6), 2 * Math.tanh(-0.7), -Math.tanh(0.6) - Math.tanh(-0.7) + 0.2};
        double sum = Math.exp(z[0]) + Math.exp(z[1]) + Math.exp(z[2]);

        assertThat(model.predict(new double[]{0.6, 0.0, 0.8}).probs()[1]).isCloseTo(Math.exp(z[1]) / sum, within(1e-12));
    }

    @Test
    void mlpUnknownActivationIsRejected() {
        assertThatThrownBy(() -> EmbeddingIntentModel.fromJson(mlpJson("gelu")))
                .isInstanceOf(MlIntentModel.InvalidModelException.class)
                .hasMessageContaining("gelu");
    }

    @Test
    void mlpLayerShapesMustChain() {
        ObjectNode json = mlpJson("relu");
        ((ArrayNode) json.get("layers").get(1).get("weights").get(0)).add(1.0); // 3 cột thay vì 2

        assertThatThrownBy(() -> EmbeddingIntentModel.fromJson(json))
                .isInstanceOf(MlIntentModel.InvalidModelException.class)
                .hasMessageContaining("mong đợi 2");
    }

    // --- file thật ---

    @Test
    void shippedModelLoads() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/ml/intent-model-a2.json")) {
            assertThat(in).as("src/main/resources/ml/intent-model-a2.json").isNotNull();
            EmbeddingIntentModel model = EmbeddingIntentModel.load(in, TestModels.MAPPER);

            assertThat(model.labels()).hasSize(12).contains(Intent.EMERGENCY, Intent.OFF_TOPIC);
            assertThat(model.dimension()).isEqualTo(768);
            assertThat(model.embeddingModel()).isEqualTo("gemini-embedding-2");
            assertThat(model.taskType()).isEqualTo("CLASSIFICATION");
        }
    }
}
