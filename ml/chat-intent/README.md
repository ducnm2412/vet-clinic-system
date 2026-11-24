# chat-intent — bộ phân loại ý định (A1: TF-IDF + hồi quy logistic)

Hợp đồng xuất mô hình cho Java: `MODEL_FORMAT.md`. Hướng dẫn gán nhãn: `LABELING.md`.

## Cài đặt (một lần)

```bash
python -m venv .venv
.venv/Scripts/python -m pip install -r requirements.txt     # Linux/macOS: .venv/bin/python
```

## Thứ tự chạy

```bash
PY=.venv/Scripts/python
$PY -m pytest                 # kiểm thử (dùng dữ liệu giả trong thư mục tạm)
$PY prepare.py                # data/raw/*.csv -> data/train.csv, val.csv, test.csv (70/15/15, seed 42)
$PY eval_rules.py             # baseline luật trên val -> results/rules_val.md
$PY train.py                  # chọn C/khối char + threshold trên val -> model/intent-model.json, parity.json, training-log.json
$PY eval.py                   # rule / ml / hybrid trên val -> results/a1.md
$PY eval.py --test            # CHỈ chạy một lần ở bước cuối (thêm tập test)

# A2 (cần GEMINI_API_KEY trong env hoặc .env ở gốc repo; cache ở data/embeddings/, không commit)
$PY embed.py                  # embedding gemini-embedding-001 & -2 cho train/val/test (chạy lại chỉ gọi câu mới)
$PY train_a2.py               # logreg / MLP / A1+A2 trên val + đo độ trễ -> model/intent-model-a2.json, parity-a2.json, results/a2.md
```

## Tệp

| tệp | vai trò |
|---|---|
| `textnorm.py` | `normalize()` giống hệt `TextNormalizer.java`; `word_terms()`, `char_terms()` (MODEL_FORMAT mục 2) |
| `rules.py` | bản Python duy nhất của `RuleIntentClassifier.java` (cùng regex) |
| `common.py` | đọc dữ liệu, chỉ số, `predict_from_json()` (suy luận thuần Python từ JSON, giống Java) |
| `prepare.py` | gộp, chuẩn hoá nhãn, khử trùng, gom nhóm biến thể, chia phân tầng |
| `eval_rules.py` | baseline luật: 12 nhãn và 7 nhãn |
| `train.py` | huấn luyện, chọn siêu tham số/threshold, xuất JSON + parity |
| `eval.py` | so sánh rule / ml / hybrid |
| `embed.py` | A2: gọi Gemini embedContent/batchEmbedContents (backoff 429/503), cache `.npy` + chỉ mục câu |
| `train_a2.py` | A2: logreg + MLP trên embedding, trộn A1+A2, đo độ trễ, xuất `intent-model-a2.json` (MODEL_FORMAT mục A2) |

## A3 — PhoBERT (tuỳ chọn, chỉ để so sánh)

Notebook Google Colab `phobert/phobert_intent_colab.ipynb` fine-tune `vinai/phobert-base-v2` trên cùng train/val/test
và xuất `results/phobert.md` cùng định dạng với `a1.md`. Không chạy cục bộ (cần GPU và PyTorch) và không triển khai.
Cách chạy và lý do không triển khai: `phobert/README.md`.
