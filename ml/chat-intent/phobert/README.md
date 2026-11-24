# A3 — PhoBERT (bước C7, tuỳ chọn): chỉ để so sánh trong báo cáo

Notebook `phobert_intent_colab.ipynb` fine-tune `vinai/phobert-base-v2` cho 12 nhãn ý định (`LABELING.md`) trên
**cùng** `data/train.csv`, `val.csv`, `test.csv` với A1/A2, rồi xuất `results/phobert.md` cùng định dạng với
`results/a1.md`. Mô hình này **không** được đưa vào `chat-service`.

Notebook chưa được chạy trên máy dev (không có GPU, không cài PyTorch cục bộ); mới chỉ kiểm tra JSON/nbformat, cú
pháp các ô code và hàm sinh bảng markdown (chạy với dự đoán giả).

## Chạy trên Google Colab

1. Mở <https://colab.research.google.com> → **File → Upload notebook** → chọn
   `ml/chat-intent/phobert/phobert_intent_colab.ipynb`.
2. **Runtime → Change runtime type → T4 GPU** → Save.
3. Ở ô **Cấu hình** (mục 1), chọn cách đưa dữ liệu vào:
   - `DATA_SOURCE = "upload"` (mặc định): khi chạy tới mục 3, chọn 3 file `ml/chat-intent/data/train.csv`,
     `val.csv`, `test.csv` trên máy.
   - `DATA_SOURCE = "clone"`: thay `REPO_URL` bằng URL repo thật. Repo riêng tư thì tạo GitHub fine-grained token
     chỉ-đọc, lưu vào **Colab Secrets** (biểu tượng chìa khoá) với tên `GH_TOKEN` và bật quyền cho notebook. Không dán
     token vào notebook.
   - `DATA_SOURCE = "drive"`: chép 3 file vào `MyDrive/vet-clinic-intent/`.
4. **Runtime → Run all**. Mục 3 dừng chờ chọn file (nếu dùng `upload`).
5. Cuối notebook trình duyệt tự tải về `phobert.md` và `phobert-predictions.csv`. Chép `phobert.md` vào
   `ml/chat-intent/results/phobert.md`. File dự đoán chỉ để tra cứu, không bắt buộc commit.

Các tuỳ chọn khác trong ô Cấu hình: `MODEL_NAME` (`vinai/phobert-base` để so với bản gốc), `SEGMENTER`
(`vncorenlp` / `underthesea` / `none`), `LEARNING_RATES` (mặc định 2e-5, 3e-5, 5e-5), `NUM_EPOCHS` = 10,
`PATIENCE` = 2, `USE_CLASS_WEIGHTS`, `SEED` = 42, `CPU_THREADS` = 2.

**Tập test chỉ được đánh giá một lần.** Ô ở mục 9 ghi dấu `TEST_DA_CHAY`; chạy lại sẽ báo lỗi, trừ khi đặt
`FORCE_RERUN_TEST = True` (khi đó ghi rõ trong báo cáo). Không quay lại chỉnh siêu tham số theo kết quả test.

## Thời gian ước tính trên Colab T4 (chưa chạy thực tế)

| bước | ước tính |
|---|---|
| cài thư viện, tải VnCoreNLP và PhoBERT (~540 MB) | 2–4 phút |
| tách từ ~1 025 câu | dưới 30 giây |
| huấn luyện một learning rate (≤ 10 epoch × 45 bước, fp16) | 1–3 phút |
| lưới 3 learning rate | 4–9 phút |
| đánh giá val/test và đo độ trễ GPU/CPU | 1–2 phút |
| **tổng** | **khoảng 10–15 phút** |

GPU miễn phí của Colab có thể không có sẵn vào giờ cao điểm. Nếu chỉ có CPU, notebook vẫn chạy nhưng huấn luyện mất
khoảng 30–60 phút.

## Kết quả đầu ra (`results/phobert.md`)

- Cấu hình, phiên bản torch/transformers, GPU, bộ tách từ thực tế đã dùng.
- Lưới learning rate trên val: epoch tốt nhất, accuracy, macro-F1, recall EMERGENCY, số báo nhầm EMERGENCY, thời gian.
- Cho **val** và **test**: bảng cùng cột với `a1.md` (acc/macro-F1 12 nhãn và 7 nhãn, precision/recall/báo nhầm
  EMERGENCY, ms/câu); bảng theo nhóm câu (`source` persona/variant/hard_negative, có dấu/không dấu); tối đa 20 câu sai;
  precision/recall/F1 từng nhãn; ma trận nhầm lẫn.
- Độ trễ từng câu (batch = 1, 154 câu val): tách từ, token hoá + mô hình, trung vị và p95, trên GPU T4 và trên CPU
  2 luồng.

## Lựa chọn kỹ thuật

- **`phobert-base-v2` thay vì `phobert-base`:** cùng kiến trúc và kích thước (RoBERTa-base, ~135 triệu tham số), nhưng
  v2 được tiền huấn luyện thêm ~120 GB văn bản web (OSCAR-2301) ngoài 20 GB Wikipedia + tin tức của bản gốc. Tin nhắn
  chatbot là văn nói, teencode và viết tắt, gần với văn bản web hơn, trong khi chi phí không đổi.
- **Tách từ bắt buộc:** PhoBERT học trên văn bản đã tách từ bằng RDRSegmenter (VnCoreNLP), các âm tiết của một từ nối
  bằng `_`. Notebook dùng `py_vncorenlp` (cần Java, tự cài nếu thiếu). Nếu không khởi động được thì tự chuyển sang
  `underthesea.word_tokenize` và ghi rõ trong kết quả.
- **Điểm yếu với câu không dấu:** khoảng 30 % dữ liệu không dấu hoặc teencode. Bộ tách từ và từ vựng BPE đều học trên
  văn bản có dấu, nên các câu này gần như không được ghép từ và bị cắt thành nhiều mảnh BPE. Kết quả có tách riêng
  nhóm có dấu và không dấu để thấy điều này. Notebook không thêm bước khôi phục dấu, để so sánh công bằng với A1/A2.
- **Huấn luyện:** HF `Trainer`, max_len 64, batch 16, warmup 10 %, weight decay 0.01, fp16, tối đa 10 epoch, early
  stopping theo macro-F1 val (patience 2) và nạp lại checkpoint tốt nhất. Giữa các learning rate, chọn theo **recall
  EMERGENCY trước, rồi macro-F1**, giống A1. Seed 42. Trọng số lớp là tuỳ chọn (dữ liệu khá cân bằng, 31–96 câu/nhãn).
- `transformers` được ghim ở nhánh 4.x (`>=4.44,<5`) vì notebook dùng `eval_strategy` và tokenizer "slow" của PhoBERT.

## Vì sao không triển khai

- **Phụ thuộc nặng:** cần PyTorch (hoặc ONNX Runtime) cạnh một service Java. Trọng số ~540 MB (fp32), trong khi A1 chỉ
  là một file JSON khoảng 1 MB, đọc bằng Java thuần.
- **Bộ nhớ:** nạp mô hình cần khoảng 0,6–1 GB RAM, quá lớn cho VM nhỏ đang chạy cả hệ thống microservice.
- **Độ trễ:** trên CPU 2 luồng, mỗi câu dự kiến mất vài chục ms (số thật xem `phobert.md`), cộng thêm tách từ qua JVM
  của VnCoreNLP. A1 mất khoảng 0,2 ms/câu. Chatbot đã có A1 (+ A2 khi A1 không chắc) và luật EMERGENCY/OFF_TOPIC ghi đè,
  nên PhoBERT chỉ có giá trị làm mốc tham chiếu xem một mô hình ngôn ngữ tiếng Việt fine-tune đạt mức nào trên cùng dữ liệu.
- **Dữ liệu nhỏ:** 716 câu train, val/test mỗi tập khoảng 155 câu, nên khoảng tin cậy 95 % của accuracy rộng khoảng
  ±4 điểm. Chênh lệch nhỏ giữa PhoBERT và A1/A2 không có ý nghĩa thống kê.
