# Định dạng `intent-model.json` (hợp đồng Python ↔ Java)

Python (`train.py`) xuất file này; Java (`MlIntentClassifier`) đọc và tính lại **y hệt**. Mọi thay đổi
phải sửa cả hai phía và chạy lại test đối chiếu.

## 1. Chuẩn hoá chữ — giống hệt `TextNormalizer.java`

1. `None`/null → `""`.
2. Unicode NFD, bỏ mọi ký tự dấu kết hợp (category `M*`).
3. `đ`→`d`, `Đ`→`D`.
4. Chữ thường (`lower()` / `toLowerCase(Locale.ROOT)`).
5. Mọi chuỗi ký tự **không phải chữ cái hay chữ số** (Unicode `L*`, `N*`) → một dấu cách.
6. Bỏ cách đầu/cuối, gộp nhiều cách thành một.

Câu chuẩn hoá rỗng → không tính đặc trưng, coi là `OTHER`.

## 2. Đặc trưng (hai khối, không dùng analyzer mặc định của sklearn)

`tokens = normalized.split(" ")`

- **Khối `word`**: mọi unigram `tokens[i]` và bigram `tokens[i] + " " + tokens[i+1]`.
- **Khối `char`**: với mỗi token `t`: `w = " " + t + " "`; với `n` từ `char_ngram_min` đến `char_ngram_max`
  (2..5): mọi chuỗi con độ dài `n` của `w` (`w[i:i+n]`, `i = 0..len(w)-n`); bỏ qua nếu `len(w) < n`.

Mỗi khối riêng:
1. Đếm tần suất `tf` của các term **có trong `vocab`** (term lạ bỏ qua).
2. `tf' = 1 + ln(tf)` (sublinear).
3. `x[j] = tf'[j] * idf[j]`.
4. Chuẩn hoá L2 trong khối (norm = 0 thì để nguyên vector 0).

Vector cuối = nối `word` rồi `char`: chỉ số toàn cục = `offset` của khối + chỉ số trong khối.

## 3. Phân loại

`logit[k] = intercept[k] + Σ_j coef[k][j] * x[j]`, `p = softmax(logit)` (trừ max trước khi exp).
Nhãn = argmax. `confidence = p[argmax]`.

## 4. JSON

```json
{
  "version": 1,
  "kind": "tfidf-logreg",
  "trained_at": "2026-11-24T10:00:00Z",
  "labels": ["BOOKING", "CARE_ADVICE", "..."],
  "threshold": 0.55,
  "blocks": [
    {"name": "word", "offset": 0,    "vocab": {"dat lich": 0, "...": 1}, "idf": [2.1, 3.4]},
    {"name": "char", "offset": 1234, "vocab": {" me": 0, "...": 1},     "idf": [1.7, 2.2],
     "ngram_min": 2, "ngram_max": 5}
  ],
  "coef": [[0.0, 0.12, "... (n_labels x n_features, làm tròn 6 chữ số)"]],
  "intercept": [0.1, -0.3]
}
```

- `labels[k]` khớp hàng `coef[k]` và `intercept[k]`.
- `threshold`: dưới ngưỡng → hệ thống coi là không chắc chắn (xem `CompositeIntentClassifier`).
- Hệ số làm tròn 6 chữ số thập phân để file nhỏ; test đối chiếu so xác suất với dung sai `1e-4`.

## 5. Tệp đối chiếu

`train.py` xuất kèm `parity.json`: `[{"text": "...", "label": "...", "probs": [...]}]` cho 50 câu
(gồm câu có dấu, không dấu, ký tự lạ, câu rỗng). Test Java đọc `parity.json` và so khớp.

## A2. `intent-model-a2.json` — embedding Gemini + bộ phân loại nhỏ

Xuất bởi `train_a2.py`; suy luận tham chiếu: `common.predict_a2_from_json`. Không dùng chuẩn hoá chữ mục 1 cho
đặc trưng — văn bản gốc gửi thẳng tới Gemini (nhưng câu chuẩn hoá rỗng vẫn coi là `OTHER`, không gọi API).

1. **Embedding**: `POST https://generativelanguage.googleapis.com/v1beta/models/{embedding.model}:embedContent`,
   header `x-goog-api-key`, body
   `{"content":{"parts":[{"text": <câu>}]},"taskType": <embedding.task_type>,"outputDimensionality": <embedding.output_dimensionality>}`.
   Vector = `response.embedding.values` (phải đúng `output_dimensionality` chiều).
2. **Chuẩn hoá L2**: `x = v / ||v||` (norm = 0 thì giữ nguyên).
3. **Phân loại** theo `kind`:
   - `embedding-logreg`: `logit[k] = intercept[k] + Σ_j coef[k][j] * x[j]`; `p = softmax(logit)`.
   - `embedding-mlp`: `h0 = x`; với mỗi phần tử `layers[l]`: `z[i] = bias[i] + Σ_j weights[i][j] * h[j]`
     (`weights` là ma trận **số nơ-ron ra × số đầu vào**), `h = activation(z)` với `activation` ∈
     `relu` (max(0,z)), `tanh`, `identity`, `softmax` (lớp cuối, trừ max trước khi exp). `p = h` của lớp cuối.
4. Nhãn = argmax `p` theo `labels`, `confidence = p[argmax]`; dưới `threshold` → không chắc chắn (như A1).

```json
{
  "version": 1, "kind": "embedding-logreg | embedding-mlp", "trained_at": "...",
  "embedding": {"provider": "gemini", "model": "gemini-embedding-001", "endpoint": "embedContent",
                "task_type": "CLASSIFICATION", "output_dimensionality": 768, "normalize": "l2"},
  "labels": ["BOOKING", "..."], "threshold": 0.4,
  "coef": [[...768 số...]], "intercept": [...],                       // chỉ khi embedding-logreg
  "layers": [{"weights": [[...]], "bias": [...], "activation": "relu"},  // chỉ khi embedding-mlp
             {"weights": [[...]], "bias": [...], "activation": "softmax"}],
  "a1_blend": {"w_a2": 0.5, "a2_kind": "mlp", "note": "..."}           // tham khảo, không bắt buộc
}
```

Hệ số làm tròn 6 chữ số. **Đối chiếu**: `parity-a2.json` = `[{"text", "label", "probs", "embedding"}]` (20 câu val;
`embedding` là vector đã chuẩn hoá L2 lấy từ cache, nên Java kiểm chứng phần phân loại mà không cần gọi mạng).
Dung sai xác suất `1e-4`. Lưu ý: embedding gọi lại từ API có thể lệch nhẹ so với cache (không xác định tuyệt đối),
nên test Java phải dùng vector trong `parity-a2.json`, không gọi API.
