# Đánh giá cuối trên tập TEST (bước C5)

Tập test: 155 câu, để riêng từ lúc chia dữ liệu, **chạy đúng một lần** bằng các mô hình đã xuất (`intent-model.json` trained_at 2026-11-24T04:13:40Z, `intent-model-a2.json` trained_at 2026-11-24T04:38:10Z); không chỉnh siêu tham số/ngưỡng nào sau khi thấy số test. Sinh bởi `python eval.py --test`.

Chính sách A1+A2 giống `CompositeIntentClassifier` + `application.yml`: luật EMERGENCY/OFF_TOPIC ghi đè; A1 conf < 0.6 và câu ≥ 3 từ → gọi A2, A2 conf ≥ 0.4 → nhãn A2; ngược lại A1 conf ≥ 0.3 → nhãn A1; còn lại → nhãn luật. A2 dùng embedding trong cache (`embed.py`, cùng tham số Java gửi).

## Bảng chính — TEST

| hệ | acc | macro-F1 (12) | EMERG recall | EMERG precision | EMERG báo nhầm | OFF_TOPIC recall | độ trễ/câu |
|---|---:|---:|---:|---:|---:|---:|---:|
| rule (luật) | 0.2710 | 0.3267 | 0.3500 (7/20) | 1.0000 | 0 | 0.5455 | 0.028 ms |
| A1 (TF-IDF+logreg) | 0.8258 | 0.8133 | 0.9500 (19/20) | 0.9500 | 1 | 0.8182 | 0.187 ms |
| A1-hybrid (t=0.3) | 0.8065 | 0.7994 | 0.9500 (19/20) | 1.0000 | 0 | 0.9091 | 0.215 ms |
| A2 [gemini-embedding-2, logreg] | 0.9613 | 0.9533 | 1.0000 (20/20) | 1.0000 | 0 | 1.0000 | 572 ms (p95 643) + 0.54 ms |
| A1+A2 (chính sách CompositeIntentClassifier) | 0.9226 | 0.9091 | 1.0000 (20/20) | 1.0000 | 0 | 0.9091 | 0.22 ms, 40% tin thêm API |
| A2 tham khảo [gemini-embedding-001, logreg C=0.3] | 0.8452 | 0.8391 | 1.0000 (20/20) | 0.9091 | 2 | 0.9091 | — |

Độ trễ đo bằng Python thuần (trung bình 155 câu × 5 lượt, máy dev); Java (`MlIntentClassifier`) sẽ khác (thường nhanh hơn sau khi JIT khởi động). Cột A1+A2: phần cục bộ + tỷ lệ tin phải gọi thêm API.
`embedContent` thật (gemini-embedding-2, 20 lần tuần tự, mạng hiện tại): trung vị 572 ms, p95 643 ms, min 541, max 648.

Nguồn nhãn của A1+A2 trên test: a1 81, a2 59, rule 2, rule-override 13; gọi API 62/155 câu.

Khoảng tin cậy 95% của macro-F1 (bootstrap 1000 lần theo câu, seed 42): rule (luật): [0.255, 0.378]; A1-hybrid (t=0.3): [0.717, 0.855]; A2 [gemini-embedding-2, logreg]: [0.909, 0.986]; A1+A2 (chính sách CompositeIntentClassifier): [0.849, 0.953]. Chênh A1+A2 − A1-hybrid: trung vị +0.112, 95% [+0.068, +0.163].

### F1 từng nhãn — TEST

| nhãn (support) | rule | A1 | A1-hybrid | A2 | A1+A2 | tham khảo 1 |
|---|---:|---:|---:|---:|---:|---:|
| BOOKING (15) | 0.000 | 0.897 | 0.815 | 0.966 | 0.933 | 0.897 |
| CARE_ADVICE (21) | 0.000 | 0.909 | 0.905 | 0.977 | 0.977 | 0.927 |
| CONTACT (11) | 0.286 | 0.762 | 0.667 | 1.000 | 0.900 | 0.957 |
| EMERGENCY (20) | 0.519 | 0.950 | 0.974 | 1.000 | 1.000 | 0.952 |
| GREETING (8) | 0.545 | 0.824 | 0.824 | 0.824 | 0.824 | 0.857 |
| OFF_TOPIC (11) | 0.706 | 0.857 | 0.952 | 1.000 | 0.952 | 0.909 |
| OPENING_HOURS (11) | 0.778 | 0.880 | 0.917 | 0.957 | 0.917 | 0.762 |
| OTHER (11) | 0.164 | 0.737 | 0.562 | 0.957 | 0.870 | 0.615 |
| PRICE (14) | 0.000 | 0.710 | 0.733 | 1.000 | 0.933 | 0.800 |
| PRODUCT (14) | 0.000 | 0.769 | 0.750 | 0.929 | 0.889 | 0.720 |
| SERVICE_INFO (12) | 0.000 | 0.609 | 0.636 | 0.909 | 0.857 | 0.750 |
| THANKS (7) | 0.923 | 0.857 | 0.857 | 0.923 | 0.857 | 0.923 |

### Cùng bảng trên VAL (cùng mô hình, cùng chính sách)

| hệ | acc | macro-F1 (12) | EMERG recall | EMERG precision | EMERG báo nhầm | OFF_TOPIC recall |
|---|---:|---:|---:|---:|---:|---:|
| rule (luật) | 0.2403 | 0.2628 | 0.5000 (10/20) | 0.9091 | 1 | 0.3000 |
| A1 (TF-IDF+logreg) | 0.8377 | 0.8245 | 1.0000 (20/20) | 0.9524 | 1 | 0.6000 |
| A1-hybrid (t=0.3) | 0.8377 | 0.8259 | 1.0000 (20/20) | 0.9091 | 2 | 0.5000 |
| A2 [gemini-embedding-2, logreg] | 0.9351 | 0.9323 | 1.0000 (20/20) | 1.0000 | 0 | 1.0000 |
| A1+A2 (chính sách CompositeIntentClassifier) | 0.8961 | 0.8879 | 1.0000 (20/20) | 0.9524 | 1 | 0.9000 |
| A2 tham khảo [gemini-embedding-001, logreg C=0.3] | 0.8442 | 0.8396 | 0.9500 (19/20) | 0.8636 | 3 | 0.8000 |

Val đã dùng để chọn siêu tham số/ngưỡng nên số val lạc quan hơn; chênh val − test ước lượng mức quá khớp.

### A1+A2 (chính sách CompositeIntentClassifier) chi tiết — test

n = 155  |  accuracy = 0.9226  |  macro-F1 = 0.9091

EMERGENCY: recall = 1.0000 (20/20), precision = 1.0000, báo nhầm (FP) = 0

| nhãn | precision | recall | F1 | support |
|---|---:|---:|---:|---:|
| BOOKING | 0.933 | 0.933 | 0.933 | 15 |
| CARE_ADVICE | 0.955 | 1.000 | 0.977 | 21 |
| CONTACT | 1.000 | 0.818 | 0.900 | 11 |
| EMERGENCY | 1.000 | 1.000 | 1.000 | 20 |
| GREETING | 0.778 | 0.875 | 0.824 | 8 |
| OFF_TOPIC | 1.000 | 0.909 | 0.952 | 11 |
| OPENING_HOURS | 0.846 | 1.000 | 0.917 | 11 |
| OTHER | 0.833 | 0.909 | 0.870 | 11 |
| PRICE | 0.875 | 1.000 | 0.933 | 14 |
| PRODUCT | 0.923 | 0.857 | 0.889 | 14 |
| SERVICE_INFO | 1.000 | 0.750 | 0.857 | 12 |
| THANKS | 0.857 | 0.857 | 0.857 | 7 |

Ma trận nhầm lẫn (hàng = thật, cột = dự đoán):

```
thật\dự        BOOKIN CARE_A CONTAC EMERGE GREETI OFF_TO OPENIN  OTHER  PRICE PRODUC SERVIC THANKS
BOOKING            14      0      0      0      0      0      1      0      0      0      0      0
CARE_ADVICE         0     21      0      0      0      0      0      0      0      0      0      0
CONTACT             0      0      9      0      0      0      1      1      0      0      0      0
EMERGENCY           0      0      0     20      0      0      0      0      0      0      0      0
GREETING            0      0      0      0      7      0      0      0      0      1      0      0
OFF_TOPIC           0      0      0      0      0     10      0      0      1      0      0      0
OPENING_HOURS       0      0      0      0      0      0     11      0      0      0      0      0
OTHER               0      0      0      0      0      0      0     10      0      0      0      1
PRICE               0      0      0      0      0      0      0      0     14      0      0      0
PRODUCT             0      0      0      0      0      0      0      1      1     12      0      0
SERVICE_INFO        1      1      0      0      1      0      0      0      0      0      9      0
THANKS              0      0      0      0      1      0      0      0      0      0      0      6
```

### A2 [gemini-embedding-2, logreg] chi tiết — test

n = 155  |  accuracy = 0.9613  |  macro-F1 = 0.9533

EMERGENCY: recall = 1.0000 (20/20), precision = 1.0000, báo nhầm (FP) = 0

| nhãn | precision | recall | F1 | support |
|---|---:|---:|---:|---:|
| BOOKING | 1.000 | 0.933 | 0.966 | 15 |
| CARE_ADVICE | 0.955 | 1.000 | 0.977 | 21 |
| CONTACT | 1.000 | 1.000 | 1.000 | 11 |
| EMERGENCY | 1.000 | 1.000 | 1.000 | 20 |
| GREETING | 0.778 | 0.875 | 0.824 | 8 |
| OFF_TOPIC | 1.000 | 1.000 | 1.000 | 11 |
| OPENING_HOURS | 0.917 | 1.000 | 0.957 | 11 |
| OTHER | 0.917 | 1.000 | 0.957 | 11 |
| PRICE | 1.000 | 1.000 | 1.000 | 14 |
| PRODUCT | 0.929 | 0.929 | 0.929 | 14 |
| SERVICE_INFO | 1.000 | 0.833 | 0.909 | 12 |
| THANKS | 1.000 | 0.857 | 0.923 | 7 |

Ma trận nhầm lẫn (hàng = thật, cột = dự đoán):

```
thật\dự        BOOKIN CARE_A CONTAC EMERGE GREETI OFF_TO OPENIN  OTHER  PRICE PRODUC SERVIC THANKS
BOOKING            14      0      0      0      0      0      1      0      0      0      0      0
CARE_ADVICE         0     21      0      0      0      0      0      0      0      0      0      0
CONTACT             0      0     11      0      0      0      0      0      0      0      0      0
EMERGENCY           0      0      0     20      0      0      0      0      0      0      0      0
GREETING            0      0      0      0      7      0      0      0      0      1      0      0
OFF_TOPIC           0      0      0      0      0     11      0      0      0      0      0      0
OPENING_HOURS       0      0      0      0      0      0     11      0      0      0      0      0
OTHER               0      0      0      0      0      0      0     11      0      0      0      0
PRICE               0      0      0      0      0      0      0      0     14      0      0      0
PRODUCT             0      0      0      0      0      0      0      1      0     13      0      0
SERVICE_INFO        0      1      0      0      1      0      0      0      0      0     10      0
THANKS              0      0      0      0      1      0      0      0      0      0      0      6
```

### A1+A2 sai 12/155 câu test

| # | câu | thật | A1+A2 (nguồn) | luật | A1 (conf) | A2 (conf) |
|---:|---|---|---|---|---|---|
| 1 | Có lưu hồ sơ bệnh án online để xem lại lịch tiêm không? | SERVICE_INFO | BOOKING (a1) | OTHER | BOOKING (0.31) | SERVICE_INFO (0.29) |
| 2 | dạ | OTHER | THANKS (a1) | OTHER | THANKS (0.38) | OTHER (0.75) |
| 3 | cat dau phu tfor gia sao a | PRODUCT | OTHER (rule) | OTHER | PRICE (0.24) | OTHER (0.37) |
| 4 | Bát ăn inox cho chó lớn giá bao nhiêu? | PRODUCT | PRICE (a1) | OTHER | PRICE (0.85) | PRODUCT (0.89) |
| 5 | tính giùm 2345 nhân 678 bằng bao nhiêu | OFF_TOPIC | PRICE (a1) | OTHER | PRICE (0.90) | OFF_TOPIC (0.45) |
| 6 | mai 9h con cho ko | BOOKING | OPENING_HOURS (a2) | OTHER | BOOKING (0.22) | OPENING_HOURS (0.52) |
| 7 | vậy thôi chào nha | THANKS | GREETING (a1) | OTHER | GREETING (0.69) | THANKS (0.67) |
| 8 | shop oiii | GREETING | PRODUCT (a1) | OTHER | PRODUCT (0.50) | GREETING (0.71) |
| 9 | Số zalo phòng khám là gì, tối nay mấy giờ còn trực? | CONTACT | OPENING_HOURS (a1) | CONTACT | OPENING_HOURS (0.66) | CONTACT (0.55) |
| 10 | co fanpage ko ad, cho mik link | CONTACT | OTHER (rule) | OTHER | CONTACT (0.20) | CONTACT (0.37) |
| 11 | Siêu âm thai cho chó thì làm như thế nào, có đau bé không? | SERVICE_INFO | CARE_ADVICE (a2) | OTHER | CARE_ADVICE (0.35) | CARE_ADVICE (0.50) |
| 12 | chào bác sĩ, có siêu âm cho chó không ạ | SERVICE_INFO | GREETING (a2) | OTHER | GREETING (0.47) | GREETING (0.75) |

<!-- NHẬN XÉT -->

## Nhận xét (viết tay, phần này được giữ nguyên khi chạy lại `eval.py --test`)

### Đối chiếu tiêu chí đạt (ke-hoach-chatbot.md, C5)

| Tiêu chí | A1-hybrid (luật + A1) | A1+A2 (đang chạy, có API key) |
|---|---|---|
| Recall EMERGENCY ≥ 0,98 | **Không đạt**: 0,95 (19/20) | **Đạt**: 1,00 (20/20) |
| Recall EMERGENCY không thấp hơn luật (0,35) | Đạt | Đạt |
| Macro-F1 hybrid > luật (0,327) | Đạt: 0,799 | Đạt: 0,909 |
| Độ trễ < 5 ms/câu | Đạt: ~0,2 ms (Python) | Phần cục bộ đạt (~0,2 ms); 40% tin gọi thêm `embedContent` ~0,57 s (p95 0,64 s) |

- Với 20 câu EMERGENCY, mỗi câu sót làm recall giảm 0,05, nên "≥ 0,98" thực chất là "không sót câu nào". A1 (và
  A1-hybrid) sót đúng một câu: *"mèo nhà em ăn mấy lá hoa ly trong bình, giờ nôn hoài"* → `CARE_ADVICE` (0,42). Luật
  không có từ "hoa ly" và dữ liệu huấn luyện thiếu các ca ngộ độc thực vật (hoa ly cực độc với mèo — kiến thức chuyên môn
  mà TF-IDF không thể suy ra). A2 nhận đúng `EMERGENCY`, và vì A1 chỉ tin 0,42 < 0,6 nên chính sách A1+A2 gọi A2 → đạt
  20/20. Nghĩa là **khi tắt A2 (không có key, hết hạn mức, lỗi mạng) hệ thống không đạt tiêu chí recall EMERGENCY trên
  test**; cần bổ sung dữ liệu ngộ độc (hoa ly, nho, socola, xylitol…) vào train và/hoặc thêm từ khoá vào luật
  EMERGENCY — việc này phải làm trên dữ liệu mới, không dựa vào test.
- Độ trễ 5 ms là tiêu chí cho bộ phân loại cục bộ; A2 là lời gọi mạng nên không thể đạt mức đó. Tác động thực tế nhỏ vì
  chat-service vốn gọi Gemini sinh câu trả lời mất vài giây, A2 có timeout 1,2 s và lỗi thì rơi về A1. Số ms của Java
  sẽ khác số đo Python (thường nhanh hơn sau khi JIT khởi động).

### Val so với test

| Hệ | macro-F1 val | macro-F1 test | chênh | EMERGENCY val → test |
|---|---:|---:|---:|---|
| luật | 0,263 | 0,327 | +0,064 | 10/20 → 7/20 |
| A1 | 0,825 | 0,813 | −0,011 | 20/20 → 19/20 |
| A1-hybrid | 0,826 | 0,799 | −0,027 | 20/20 → 19/20 |
| A2 (`gemini-embedding-2`) | 0,932 | 0,953 | +0,021 | 20/20 → 20/20 |
| A1+A2 (chính sách thật) | 0,888 | 0,909 | +0,021 | 20/20 → 20/20 |
| A2 tham khảo (`gemini-embedding-001`) | 0,840 | 0,839 | −0,001 | 19/20 → 20/20 |

Mọi chênh lệch đều nằm trong khoảng nhiễu (khoảng tin cậy 95% của macro-F1 trên 155 câu rộng ±0,05–0,07), nên không
thấy dấu hiệu quá khớp vào val. Thứ hạng các hệ giữ nguyên: luật ≪ A1 ≈ A1-hybrid < A1+A2 < A2. Lợi thế của A1+A2 so
với A1-hybrid (+0,11 macro-F1, khoảng tin cậy bootstrap ghép cặp [+0,07; +0,16]) là có ý nghĩa thống kê.

Lưu ý: bảng mô phỏng trong `results/a2.md` (t = 0,6 → 0,918 trên val) tính tay **không** có điều kiện "câu ≥ 3 từ";
chính sách thật (min-words = 3) đạt 0,888 trên val. Trên val, bỏ điều kiện này (min-words 1–2) cho 0,925 — các câu
chào/cảm ơn rất ngắn là chỗ A1 yếu nhất mà A2 làm tốt. Có thể cân nhắc hạ `min-words` (đổi lại tốn thêm hạn mức), nhưng
quyết định đó phải dựa trên val/dữ liệu mới, không dựa trên test.

A2 đơn (0,953) tốt hơn chính sách A1+A2 (0,909) trên test: 6/12 lỗi của A1+A2 (#2, #4, #5, #7, #8, #9) là câu không gọi A2
(A1 conf ≥ 0,6, hoặc câu < 3 từ) trong khi A2 đúng. Đây là đánh đổi có chủ ý (chỉ ~40% tin gọi API, giữ
hạn mức ~1000 lần/ngày), không phải lỗi cài đặt.

### Phân tích lỗi (12 câu A1+A2 sai trên test + 1 câu EMERGENCY A1 sót)

| # | Câu | Thật → dự đoán | Nguyên nhân có thể |
|---:|---|---|---|
| 1 | Có lưu hồ sơ bệnh án online để xem lại lịch tiêm không? | SERVICE_INFO → BOOKING | Thiếu dữ liệu (câu hỏi về tính năng hồ sơ online); cụm "lịch tiêm" kéo về BOOKING; A2 cũng không chắc (0,29) |
| 2 | dạ | OTHER → THANKS | Nhập nhằng, câu 1 từ; dưới 3 từ nên không gọi A2 (A2 đúng, 0,75) |
| 3 | cat dau phu tfor gia sao a | PRODUCT → OTHER | Lỗi gõ + không dấu ("cát đậu phụ tofu"); chữ "gia" kéo về PRICE; A1 0,24 và A2 0,37 đều dưới ngưỡng → rơi về luật |
| 4 | Bát ăn inox cho chó lớn giá bao nhiêu? | PRODUCT → PRICE | Nhập nhằng theo quy ước gán nhãn (giá món hàng = PRODUCT); "giá bao nhiêu" làm A1 tự tin 0,85 nên không gọi A2 (A2 đúng) |
| 5 | tính giùm 2345 nhân 678 bằng bao nhiêu | OFF_TOPIC → PRICE | Thiếu dữ liệu lạc đề kiểu tính toán; "bao nhiêu" → PRICE với conf 0,90 (lỗi tự tin nhất) |
| 6 | mai 9h con cho ko | BOOKING → OPENING_HOURS | Không dấu + viết tắt ("còn chỗ không"); giờ giấc làm nhập nhằng với OPENING_HOURS; A1 đúng nhưng chỉ 0,22, A2 sai 0,52 |
| 7 | vậy thôi chào nha | THANKS → GREETING | Nhập nhằng: "chào" tạm biệt vs chào hỏi; thiếu dữ liệu câu kết thúc hội thoại |
| 8 | shop oiii | GREETING → PRODUCT | Kéo dài chữ ("oiii"), câu 2 từ; "shop" gắn với PRODUCT trong train; không gọi A2 (A2 đúng) |
| 9 | Số zalo phòng khám là gì, tối nay mấy giờ còn trực? | CONTACT → OPENING_HOURS | Câu hai ý định; nhãn đơn không biểu diễn được (A1 0,66 nên không gọi A2) |
| 10 | co fanpage ko ad, cho mik link | CONTACT → OTHER | Không dấu + viết tắt ("mik"); A1 và A2 đều đoán đúng CONTACT nhưng dưới ngưỡng (0,20 / 0,37) → rơi về luật OTHER |
| 11 | Siêu âm thai cho chó thì làm như thế nào, có đau bé không? | SERVICE_INFO → CARE_ADVICE | Nhập nhằng ranh giới dịch vụ ↔ chăm sóc (hỏi quy trình + cảm giác của thú) |
| 12 | chào bác sĩ, có siêu âm cho chó không ạ | SERVICE_INFO → GREETING | Lời chào đầu câu lấn át ý chính (quy ước: chào + hỏi → theo câu hỏi); thiếu dữ liệu câu ghép; cả A1 lẫn A2 sai |
| — | mèo nhà em ăn mấy lá hoa ly trong bình, giờ nôn hoài | EMERGENCY → CARE_ADVICE (chỉ A1/A1-hybrid) | Thiếu dữ liệu ngộ độc thực vật, cần kiến thức chuyên môn; A2 sửa được |

Tóm tắt nguyên nhân: nhập nhằng nhãn / đa ý định 6 câu (#2, #4, #7, #9, #11, #12), lỗi gõ – không dấu – viết tắt 4 câu
(#3, #6, #8, #10), thiếu dữ liệu 3 câu (#1, #5, hoa ly). Ngoài ra A1-hybrid báo nhầm 0 câu EMERGENCY trên test (A1 đơn
báo nhầm *"thanh toán bị trừ tiền 2 lần"* với conf 0,22, nhưng dưới ngưỡng nên hybrid bỏ qua).

### Hạn mức

Ngày chạy C5: embed bổ sung 55 câu test × 2 model (2 lần `batchEmbedContents` mỗi model, không lỗi 429) + 20 lần
`embedContent` đo độ trễ (`gemini-embedding-2`). Cache test giờ đủ 155/155 cho cả hai model.
