package com.vetclinic.chat.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Mô hình A2: embedding Gemini (embedContent) + bộ phân loại nhỏ, xuất từ ml/chat-intent/train_a2.py.
 * Định dạng và cách tính phải khớp ml/chat-intent/MODEL_FORMAT.md mục "A2" (tham chiếu Python:
 * {@code common.predict_a2_from_json}) — sửa một phía thì sửa cả phía kia và chạy lại EmbeddingParityTest.
 *
 * Lớp này chỉ làm phần phân loại từ vector đã có (không gọi mạng). Hỗ trợ {@code embedding-logreg} và
 * {@code embedding-mlp}. Bất biến sau khi nạp, dùng chung an toàn giữa các luồng.
 */
public final class EmbeddingIntentModel {

    static final String KIND_LOGREG = "embedding-logreg";
    static final String KIND_MLP = "embedding-mlp";
    static final int VERSION = 1;

    /** Một lớp MLP: {@code weights} là ma trận số nơ-ron ra × số đầu vào. */
    private record Layer(double[][] weights, double[] bias, String activation) {
    }

    private final String kind;
    private final List<Intent> labels;
    private final double threshold;
    private final String embeddingModel;
    private final String taskType;
    private final int dimension;
    /** Với logreg: một lớp duy nhất (coef, intercept, softmax). */
    private final List<Layer> layers;

    private EmbeddingIntentModel(String kind, List<Intent> labels, double threshold, String embeddingModel,
                                 String taskType, int dimension, List<Layer> layers) {
        this.kind = kind;
        this.labels = labels;
        this.threshold = threshold;
        this.embeddingModel = embeddingModel;
        this.taskType = taskType;
        this.dimension = dimension;
        this.layers = layers;
    }

    public static EmbeddingIntentModel load(InputStream in, ObjectMapper mapper) throws IOException {
        return fromJson(mapper.readTree(in));
    }

    public static EmbeddingIntentModel fromJson(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw invalid("gốc JSON phải là object");
        }
        String kind = root.path("kind").asText("");
        if (!KIND_LOGREG.equals(kind) && !KIND_MLP.equals(kind)) {
            throw invalid("kind phải là \"" + KIND_LOGREG + "\" hoặc \"" + KIND_MLP + "\", gặp \"" + kind + "\"");
        }
        int version = root.path("version").asInt(-1);
        if (version != VERSION) {
            throw invalid("chỉ hỗ trợ version " + VERSION + ", gặp " + version);
        }

        JsonNode embedding = root.path("embedding");
        if (!embedding.isObject()) {
            throw invalid("thiếu khối embedding");
        }
        String provider = embedding.path("provider").asText("gemini");
        if (!"gemini".equals(provider)) {
            throw invalid("embedding.provider chỉ hỗ trợ \"gemini\", gặp \"" + provider + "\"");
        }
        String model = embedding.path("model").asText("");
        if (model.isBlank()) {
            throw invalid("thiếu embedding.model");
        }
        String endpoint = embedding.path("endpoint").asText("embedContent");
        if (!"embedContent".equals(endpoint)) {
            throw invalid("embedding.endpoint chỉ hỗ trợ \"embedContent\", gặp \"" + endpoint + "\"");
        }
        String normalize = embedding.path("normalize").asText("l2");
        if (!"l2".equals(normalize)) {
            throw invalid("embedding.normalize chỉ hỗ trợ \"l2\", gặp \"" + normalize + "\"");
        }
        String taskType = embedding.path("task_type").asText("");
        if (taskType.isBlank()) {
            throw invalid("thiếu embedding.task_type");
        }
        JsonNode dimNode = embedding.path("output_dimensionality");
        if (!dimNode.isInt() || dimNode.asInt() <= 0) {
            throw invalid("embedding.output_dimensionality phải là số nguyên dương");
        }
        int dimension = dimNode.asInt();

        List<Intent> labels = MlIntentModel.parseLabels(root.path("labels"));
        int n = labels.size();

        JsonNode thresholdNode = root.path("threshold");
        if (!thresholdNode.isNumber()) {
            throw invalid("thiếu threshold (số)");
        }
        double threshold = thresholdNode.asDouble();

        List<Layer> layers = new ArrayList<>();
        if (KIND_LOGREG.equals(kind)) {
            JsonNode coefNode = root.path("coef");
            if (!coefNode.isArray() || coefNode.size() != n) {
                throw invalid("coef phải có " + n + " hàng (bằng số nhãn)");
            }
            double[][] coef = matrix(coefNode, "coef", dimension);
            double[] intercept = MlIntentModel.doubles(root.path("intercept"), "intercept");
            if (intercept.length != n) {
                throw invalid("intercept có " + intercept.length + " phần tử, mong đợi " + n);
            }
            layers.add(new Layer(coef, intercept, "softmax"));
        } else {
            JsonNode layersNode = root.path("layers");
            if (!layersNode.isArray() || layersNode.isEmpty()) {
                throw invalid("layers phải là mảng không rỗng");
            }
            int inputs = dimension;
            for (int l = 0; l < layersNode.size(); l++) {
                JsonNode layerNode = layersNode.get(l);
                String what = "layers[" + l + "]";
                JsonNode weightsNode = layerNode.path("weights");
                if (!weightsNode.isArray() || weightsNode.isEmpty()) {
                    throw invalid(what + ".weights phải là ma trận không rỗng");
                }
                double[][] weights = matrix(weightsNode, what + ".weights", inputs);
                double[] bias = MlIntentModel.doubles(layerNode.path("bias"), what + ".bias");
                if (bias.length != weights.length) {
                    throw invalid(what + ".bias có " + bias.length + " phần tử, mong đợi " + weights.length);
                }
                String activation = layerNode.path("activation").asText("");
                if (!List.of("relu", "tanh", "identity", "softmax").contains(activation)) {
                    throw invalid(what + ": hàm kích hoạt lạ \"" + activation + "\"");
                }
                layers.add(new Layer(weights, bias, activation));
                inputs = weights.length;
            }
            if (inputs != n) {
                throw invalid("lớp cuối có " + inputs + " đầu ra, mong đợi " + n + " (bằng số nhãn)");
            }
            if (!"softmax".equals(layers.get(layers.size() - 1).activation())) {
                throw invalid("lớp cuối phải dùng softmax");
            }
        }
        return new EmbeddingIntentModel(kind, List.copyOf(labels), threshold, model.trim(), taskType.trim(),
                dimension, List.copyOf(layers));
    }

    private static double[][] matrix(JsonNode node, String what, int columns) {
        double[][] rows = new double[node.size()][];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = MlIntentModel.doubles(node.get(i), what + "[" + i + "]");
            if (rows[i].length != columns) {
                throw invalid(what + "[" + i + "] có " + rows[i].length + " cột, mong đợi " + columns);
            }
        }
        return rows;
    }

    private static MlIntentModel.InvalidModelException invalid(String message) {
        return new MlIntentModel.InvalidModelException("Mô hình ý định A2 không hợp lệ: " + message);
    }

    public String kind() {
        return kind;
    }

    public List<Intent> labels() {
        return labels;
    }

    /** Ngưỡng tin cậy ghi trong file mô hình. */
    public double threshold() {
        return threshold;
    }

    /** Tên model embedding Gemini (vd. gemini-embedding-2). */
    public String embeddingModel() {
        return embeddingModel;
    }

    public String taskType() {
        return taskType;
    }

    /** Số chiều embedding (outputDimensionality gửi lên API). */
    public int dimension() {
        return dimension;
    }

    public boolean isConfident(MlIntentModel.Prediction prediction) {
        return prediction.confidence() >= threshold;
    }

    /**
     * Phân loại từ một vector embedding (thô hoặc đã chuẩn hoá — luôn chuẩn hoá L2 lại).
     *
     * @throws IllegalArgumentException vector sai số chiều
     */
    public MlIntentModel.Prediction predict(double[] embedding) {
        if (embedding == null || embedding.length != dimension) {
            throw new IllegalArgumentException("embedding " + (embedding == null ? 0 : embedding.length)
                    + " chiều, mô hình cần " + dimension);
        }
        double squared = 0;
        for (double v : embedding) {
            squared += v * v;
        }
        double norm = Math.sqrt(squared);
        double[] h = new double[dimension];
        for (int j = 0; j < dimension; j++) {
            h[j] = norm > 0 ? embedding[j] / norm : embedding[j];
        }

        for (Layer layer : layers) {
            double[] z = new double[layer.weights().length];
            for (int i = 0; i < z.length; i++) {
                double[] row = layer.weights()[i];
                double sum = layer.bias()[i];
                for (int j = 0; j < row.length; j++) {
                    sum += row[j] * h[j];
                }
                z[i] = sum;
            }
            h = activate(z, layer.activation());
        }

        int best = 0;
        for (int k = 1; k < h.length; k++) {
            if (h[k] > h[best]) {
                best = k;
            }
        }
        return new MlIntentModel.Prediction(labels.get(best), h[best], h);
    }

    private static double[] activate(double[] z, String activation) {
        switch (activation) {
            case "relu" -> {
                for (int i = 0; i < z.length; i++) {
                    z[i] = Math.max(0, z[i]);
                }
                return z;
            }
            case "tanh" -> {
                for (int i = 0; i < z.length; i++) {
                    z[i] = Math.tanh(z[i]);
                }
                return z;
            }
            case "identity" -> {
                return z;
            }
            default -> {
                return softmax(z);
            }
        }
    }

    private static double[] softmax(double[] z) {
        double max = Double.NEGATIVE_INFINITY;
        for (double v : z) {
            max = Math.max(max, v);
        }
        double sum = 0;
        double[] p = new double[z.length];
        for (int i = 0; i < z.length; i++) {
            p[i] = Math.exp(z[i] - max);
            sum += p[i];
        }
        for (int i = 0; i < p.length; i++) {
            p[i] /= sum;
        }
        return p;
    }
}
