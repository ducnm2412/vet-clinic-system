package com.vetclinic.chat.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Mô hình TF-IDF + hồi quy logistic xuất từ Python (ml/chat-intent/train.py) ra JSON. Định dạng và
 * cách tính phải khớp hệt ml/chat-intent/MODEL_FORMAT.md — sửa một phía thì sửa cả phía kia và chạy
 * lại MlIntentParityTest.
 *
 * Bất biến sau khi nạp (immutable), dùng chung an toàn giữa các luồng.
 */
public final class MlIntentModel {

    /** Kết quả dự đoán. {@code probs[k]} ứng với {@link #labels()}{@code .get(k)}. */
    public record Prediction(Intent intent, double confidence, double[] probs) {
    }

    /** File mô hình đọc được nhưng không đúng hợp đồng (nhãn lạ, sai kích thước...). */
    public static final class InvalidModelException extends IllegalArgumentException {
        public InvalidModelException(String message) {
            super(message);
        }
    }

    static final String KIND = "tfidf-logreg";
    static final int VERSION = 1;

    private record Block(String name, int offset, Map<String, Integer> vocab, double[] idf,
                         int ngramMin, int ngramMax) {
        int size() {
            return idf.length;
        }
    }

    private final List<Intent> labels;
    private final double threshold;
    private final List<Block> blocks;
    private final double[][] coef;
    private final double[] intercept;
    private final int featureCount;

    private MlIntentModel(List<Intent> labels, double threshold, List<Block> blocks, double[][] coef,
                          double[] intercept, int featureCount) {
        this.labels = labels;
        this.threshold = threshold;
        this.blocks = blocks;
        this.coef = coef;
        this.intercept = intercept;
        this.featureCount = featureCount;
    }

    public static MlIntentModel load(InputStream in, ObjectMapper mapper) throws IOException {
        return fromJson(mapper.readTree(in));
    }

    public static MlIntentModel fromJson(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw invalid("gốc JSON phải là object");
        }
        String kind = root.path("kind").asText("");
        if (!KIND.equals(kind)) {
            throw invalid("kind phải là \"" + KIND + "\", gặp \"" + kind + "\"");
        }
        int version = root.path("version").asInt(-1);
        if (version != VERSION) {
            throw invalid("chỉ hỗ trợ version " + VERSION + ", gặp " + version);
        }

        List<Intent> labels = parseLabels(root.path("labels"));
        int n = labels.size();

        JsonNode thresholdNode = root.path("threshold");
        if (!thresholdNode.isNumber()) {
            throw invalid("thiếu threshold (số)");
        }
        double threshold = thresholdNode.asDouble();

        JsonNode blocksNode = root.path("blocks");
        if (!blocksNode.isArray() || blocksNode.isEmpty()) {
            throw invalid("blocks phải là mảng không rỗng");
        }
        List<Block> blocks = new ArrayList<>();
        int expectedOffset = 0;
        for (JsonNode blockNode : blocksNode) {
            Block block = parseBlock(blockNode);
            if (block.offset() != expectedOffset) {
                throw invalid("block \"" + block.name() + "\" có offset " + block.offset()
                        + ", mong đợi " + expectedOffset);
            }
            expectedOffset += block.size();
            blocks.add(block);
        }
        int featureCount = expectedOffset;

        JsonNode coefNode = root.path("coef");
        if (!coefNode.isArray() || coefNode.size() != n) {
            throw invalid("coef phải có " + n + " hàng (bằng số nhãn)");
        }
        double[][] coef = new double[n][];
        for (int k = 0; k < n; k++) {
            coef[k] = doubles(coefNode.get(k), "coef[" + k + "]");
            if (coef[k].length != featureCount) {
                throw invalid("coef[" + k + "] có " + coef[k].length + " cột, mong đợi " + featureCount);
            }
        }
        double[] intercept = doubles(root.path("intercept"), "intercept");
        if (intercept.length != n) {
            throw invalid("intercept có " + intercept.length + " phần tử, mong đợi " + n);
        }
        return new MlIntentModel(List.copyOf(labels), threshold, List.copyOf(blocks), coef, intercept, featureCount);
    }

    static List<Intent> parseLabels(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            throw invalid("labels phải là mảng không rỗng");
        }
        List<Intent> labels = new ArrayList<>();
        for (JsonNode label : node) {
            String name = label.asText("");
            Intent intent;
            try {
                intent = Intent.valueOf(name);
            } catch (IllegalArgumentException e) {
                throw invalid("nhãn \"" + name + "\" không có trong enum Intent — cập nhật Intent.java "
                        + "hoặc huấn luyện lại mô hình theo LABELING.md");
            }
            if (labels.contains(intent)) {
                throw invalid("nhãn \"" + name + "\" bị lặp");
            }
            labels.add(intent);
        }
        return labels;
    }

    private static Block parseBlock(JsonNode node) {
        String name = node.path("name").asText("");
        if (!"word".equals(name) && !"char".equals(name)) {
            throw invalid("block lạ \"" + name + "\" (chỉ hỗ trợ word, char)");
        }
        if (!node.path("offset").isInt()) {
            throw invalid("block \"" + name + "\" thiếu offset");
        }
        int offset = node.path("offset").asInt();
        double[] idf = doubles(node.path("idf"), "idf của block " + name);
        JsonNode vocabNode = node.path("vocab");
        if (!vocabNode.isObject()) {
            throw invalid("block \"" + name + "\" thiếu vocab");
        }
        Map<String, Integer> vocab = new HashMap<>(vocabNode.size() * 2);
        Iterator<Map.Entry<String, JsonNode>> fields = vocabNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            int index = entry.getValue().asInt(-1);
            if (!entry.getValue().isInt() || index < 0 || index >= idf.length) {
                throw invalid("vocab \"" + name + "\": chỉ số của \"" + entry.getKey() + "\" ngoài [0, " + idf.length + ")");
            }
            vocab.put(entry.getKey(), index);
        }
        if (vocab.size() != idf.length) {
            throw invalid("block \"" + name + "\": vocab có " + vocab.size() + " term nhưng idf có " + idf.length);
        }
        int ngramMin = 0;
        int ngramMax = 0;
        if ("char".equals(name)) {
            ngramMin = intField(node, "ngram_min", "char_ngram_min");
            ngramMax = intField(node, "ngram_max", "char_ngram_max");
            if (ngramMin < 1 || ngramMax < ngramMin) {
                throw invalid("block char: ngram_min/ngram_max không hợp lệ (" + ngramMin + ", " + ngramMax + ")");
            }
        }
        return new Block(name, offset, Map.copyOf(vocab), idf, ngramMin, ngramMax);
    }

    private static int intField(JsonNode node, String name, String alias) {
        JsonNode value = node.has(name) ? node.get(name) : node.path(alias);
        if (!value.isInt()) {
            throw invalid("block char thiếu " + name);
        }
        return value.asInt();
    }

    static double[] doubles(JsonNode node, String what) {
        if (node == null || !node.isArray()) {
            throw invalid(what + " phải là mảng số");
        }
        double[] values = new double[node.size()];
        for (int i = 0; i < values.length; i++) {
            JsonNode value = node.get(i);
            if (!value.isNumber()) {
                throw invalid(what + "[" + i + "] không phải số");
            }
            values[i] = value.asDouble();
        }
        return values;
    }

    private static InvalidModelException invalid(String message) {
        return new InvalidModelException("Mô hình ý định không hợp lệ: " + message);
    }

    public List<Intent> labels() {
        return labels;
    }

    /** Ngưỡng tin cậy ghi trong file mô hình. */
    public double threshold() {
        return threshold;
    }

    public int featureCount() {
        return featureCount;
    }

    /**
     * Dự đoán ý định. Câu chuẩn hoá rỗng → OTHER với confidence 0 và probs toàn 0 (không tính đặc trưng),
     * nên tầng trên coi là "không chắc chắn".
     */
    public Prediction predict(String message) {
        String normalized = TextNormalizer.normalize(message);
        if (normalized.isEmpty()) {
            return new Prediction(Intent.OTHER, 0.0, new double[labels.size()]);
        }
        String[] tokens = normalized.split(" ");

        double[] logits = intercept.clone();
        for (Block block : blocks) {
            Map<Integer, Integer> counts = "word".equals(block.name())
                    ? countWords(tokens, block.vocab())
                    : countChars(tokens, block);
            addBlock(block, counts, logits);
        }

        double max = Double.NEGATIVE_INFINITY;
        for (double logit : logits) {
            max = Math.max(max, logit);
        }
        double[] probs = new double[logits.length];
        double sum = 0;
        for (int k = 0; k < logits.length; k++) {
            probs[k] = Math.exp(logits[k] - max);
            sum += probs[k];
        }
        int best = 0;
        for (int k = 0; k < probs.length; k++) {
            probs[k] /= sum;
            if (probs[k] > probs[best]) {
                best = k;
            }
        }
        return new Prediction(labels.get(best), probs[best], probs);
    }

    /** tf → 1 + ln(tf) → × idf → chuẩn hoá L2 trong khối → cộng vào logit. */
    private void addBlock(Block block, Map<Integer, Integer> counts, double[] logits) {
        if (counts.isEmpty()) {
            return;
        }
        int size = counts.size();
        int[] indices = new int[size];
        double[] values = new double[size];
        double squared = 0;
        int i = 0;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int j = entry.getKey();
            double value = (1.0 + Math.log(entry.getValue())) * block.idf()[j];
            indices[i] = j;
            values[i] = value;
            squared += value * value;
            i++;
        }
        double norm = Math.sqrt(squared);
        if (norm == 0) {
            return;
        }
        for (int k = 0; k < logits.length; k++) {
            double[] row = coef[k];
            double dot = 0;
            for (int t = 0; t < size; t++) {
                dot += row[block.offset() + indices[t]] * (values[t] / norm);
            }
            logits[k] += dot;
        }
    }

    /** Unigram và bigram ("a b"). */
    private static Map<Integer, Integer> countWords(String[] tokens, Map<String, Integer> vocab) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int i = 0; i < tokens.length; i++) {
            count(vocab.get(tokens[i]), counts);
            if (i + 1 < tokens.length) {
                count(vocab.get(tokens[i] + " " + tokens[i + 1]), counts);
            }
        }
        return counts;
    }

    /**
     * Char n-gram trong từng từ có đệm cách hai đầu (" " + t + " "). Cắt theo code point để khớp
     * cách Python cắt chuỗi.
     */
    private static Map<Integer, Integer> countChars(String[] tokens, Block block) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (String token : tokens) {
            int[] w = (" " + token + " ").codePoints().toArray();
            for (int n = block.ngramMin(); n <= block.ngramMax(); n++) {
                for (int i = 0; i + n <= w.length; i++) {
                    count(block.vocab().get(new String(w, i, n)), counts);
                }
            }
        }
        return counts;
    }

    private static void count(Integer index, Map<Integer, Integer> counts) {
        if (index != null) {
            counts.merge(index, 1, Integer::sum);
        }
    }
}
