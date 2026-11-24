# vet-clinic-system

Source base (cấu trúc thư mục) cho website quản lý & đặt lịch khám bệnh thú y kết hợp bán hàng trực tuyến, dựa trên kiến trúc microservices trong bản kế hoạch dự án.

## Kiến trúc tổng quan

Client (Web/App) → API Gateway → [Auth | Booking | Pet | Product | Order | Staff Service] → Message Broker (RabbitMQ) → Notification / Reporting Service

- Mỗi service có database riêng (Database per Service, PostgreSQL).
- Giao tiếp đồng bộ: REST API qua API Gateway (đăng nhập, đặt lịch, thanh toán).
- Giao tiếp bất đồng bộ: RabbitMQ qua Spring AMQP (thông báo, trừ tồn kho, cập nhật báo cáo).
- Service discovery: các service tự đăng ký với Eureka Server, tra cứu lẫn nhau qua đó thay vì IP/port cố định.

## Cấu trúc thư mục

```
vet-clinic-system/
├── docker-compose.yml          # khung chạy toàn bộ hệ thống (dev/local)
├── eureka-server/               # Spring Boot - service discovery
├── api-gateway/                 # Spring Cloud Gateway
├── services/
│   ├── auth-service/            # đăng nhập, JWT, RBAC — auth_db
│   ├── profile-service/         # hồ sơ khách, bác sĩ, nhân viên — profile_db
│   ├── pet-service/             # hồ sơ thú cưng — pet_db
│   ├── booking-service/         # đặt lịch khám, bệnh án — booking_db
│   ├── product-service/         # sản phẩm, tồn kho — product_db
│   ├── order-service/           # giỏ hàng, đơn hàng — order_db
│   ├── payment-service/         # thu tiền đơn thuốc — payment_db
│   ├── staff-service/           # chấm công, ca trực — staff_db
│   ├── notification-service/    # gửi email qua RabbitMQ
│   └── reporting-service/       # tổng hợp báo cáo cho Admin
├── frontend/                    # React/Next.js
└── shared/                      # DTO, config dùng chung (Maven module/JAR nội bộ)
```

Mỗi thư mục service có README riêng mô tả chức năng, database và công nghệ tương ứng (xem file `README.md` trong từng thư mục).

## Công nghệ chính

| Thành phần | Công nghệ |
|---|---|
| Frontend | React.js / Next.js |
| Backend | Java Spring Boot (Spring Web, Spring Data JPA) |
| Service Discovery | Spring Cloud Netflix Eureka |
| API Gateway | Spring Cloud Gateway |
| Giao tiếp service | OpenFeign / WebClient (sync), Spring AMQP (async) |
| Database | PostgreSQL (mỗi service 1 database) |
| Message Broker | RabbitMQ |
| Cache | Redis |
| Xác thực | Spring Security + JWT / OAuth2 |
| Thanh toán | VNPay / Momo API |
| Đóng gói | Docker (base image eclipse-temurin), Docker Compose |

## Chạy test

Test dùng PostgreSQL thật, nên cần các container database đang chạy:

```bash
docker compose up -d
bash scripts/create-test-databases.sh   # một lần cho mỗi máy
bash scripts/test.sh                    # tất cả service
bash scripts/test.sh booking-service    # một service
```

**Test không bao giờ chạy vào database đang dùng.** Mỗi service có database riêng cho test
(`booking_db_test`, `auth_db_test`…), khai sẵn trong `pom.xml`. Nếu có ai cố trỏ vào database
thật, `TestDatabaseGuard` dừng lượt chạy trước khi mở kết nối. Lý do và lịch sử sự cố nằm ở
VD-12 trong `docs/van-de-ton-dong.md`.

Chạy thẳng `mvn test` trong một service cũng an toàn, chỉ cần khai mật khẩu database của
service đó (`scripts/test.sh` đọc sẵn từ `.env`).

## Vận hành chatbot (chat-service)

Chatbot tư vấn khách hàng gọi Gemini; không có database, không lưu hội thoại. Mọi cấu hình nằm
trong `.env` (mẫu ở `.env.example`), đổi xong chỉ cần `docker compose up -d chat-service`, không
phải build lại.

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `GEMINI_API_KEY` | trống | Khoá Google AI Studio. Để trống thì nút chat vẫn hiện nhưng chỉ trả lời câu soạn sẵn (khẩn cấp, liên hệ, giờ mở cửa); A2 cũng tự tắt. |
| `GEMINI_MODEL` | `gemini-3.5-flash-lite` | Model chính. |
| `GEMINI_FALLBACK_MODELS` | `gemini-3.8-flash,gemini-3.7-flash,gemini-flash-lite-latest` | Model dự phòng, cách nhau dấu phẩy, thử lần lượt khi model chính lỗi hoặc hết quota. |
| `CHAT_GUEST_LIMIT` / `CHAT_USER_LIMIT` | `20` / `60` | Số tin mỗi 10 phút của khách vãng lai / khách đã đăng nhập. |
| `CHAT_DAILY_CAP` | `3000` | Trần tổng số lượt gọi Gemini mỗi ngày (mọi người cộng lại). |
| `CHAT_TRUSTED_PROXY_HOPS` | `1` | Số proxy tin cậy nối IP vào cuối `X-Forwarded-For` trước chat-service (chỉ có api-gateway). Xem "IP của khách vãng lai" bên dưới. |
| `CHAT_INTENT_MODE` | `hybrid` | Bộ phân loại ý định: `rules` (chỉ regex), `ml` (chỉ mô hình), `hybrid` (luật khẩn cấp trước, ML đủ tin cậy thì dùng nhãn ML). Ở mọi chế độ, câu luật nhận là khẩn cấp luôn ra EMERGENCY. |
| `CHAT_INTENT_A2_ENABLED` | `true` | Bật tầng phụ A2 (embedding Gemini + hồi quy logistic), chỉ gọi khi mô hình A1 không chắc. |
| `CHAT_INTENT_A2_DAILY_CAP` | `900` | Trần số lần gọi embedding mỗi ngày của A2. |

**Đổi model khi Google ngừng một model.** Xem danh sách model khoá hiện tại gọi được:

```bash
curl -H "x-goog-api-key: $GEMINI_API_KEY" https://generativelanguage.googleapis.com/v1beta/models
```

Chọn model có `generateContent` trong `supportedGenerationMethods`, sửa `GEMINI_MODEL` và/hoặc
`GEMINI_FALLBACK_MODELS` trong `.env`, rồi chạy `docker compose up -d chat-service`.

**Tắt ML ngay lập tức.** Đặt `CHAT_INTENT_MODE=rules` trong `.env` rồi
`docker compose up -d chat-service`; chatbot quay về bộ luật regex như trước khi có ML. Chỉ muốn
tắt A2 (giữ A1) thì đặt `CHAT_INTENT_A2_ENABLED=false`. Nếu không đọc được file mô hình, service tự
lùi về `rules` và ghi cảnh báo trong log.

**Hạn mức gói miễn phí.** Embedding Gemini cho khoảng 1000 lượt/ngày/model, dùng chung với script
huấn luyện, nên A2 dừng ở 900. Gặp HTTP 429, A2 tạm nghỉ 1 phút (kể cả khi là hạn mức ngày; đổi bằng
`app.chat.intent.a2.rate-limit-pause-minutes`) và trong lúc đó chatbot dùng kết quả của A1, khách không
thấy lỗi. Lỗi 5xx/timeout liên tiếp 3 lần thì A2 ngắt mạch 60 giây.

**Huấn luyện lại mô hình.** Làm theo `ml/chat-intent/README.md` (A1: `train.py`; A2: `embed.py`
rồi `train_a2.py`). Chép file xuất ra từ `ml/chat-intent/model/` (`intent-model.json`,
`intent-model-a2.json`) vào `services/chat-service/src/main/resources/ml/`, chạy test của
chat-service, rồi `docker compose up -d --build chat-service` (mô hình nằm trong jar nên phải build lại).

**Healthcheck.** Docker gọi `GET /chat/status` trong container; chỉ kiểm tra service đã lên và trả
HTTP 200, không phụ thuộc Gemini có khoá hay còn quota.

**IP của khách vãng lai.** Hạn mức khách chưa đăng nhập tính theo IP. Đường đi thực tế:
trình duyệt → Next.js `:3000` (rewrite `/api/*`) → api-gateway `:8080` → chat-service `:8092`.
Spring Cloud Gateway nối IP của bên kết nối tới nó vào cuối `X-Forwarded-For`, nên với
`CHAT_TRUSTED_PROXY_HOPS=1` chat-service lấy phần tử cuối — khách không giả được bằng header tự gửi.
Rewrite của Next.js không ghi IP trình duyệt (chỉ chuyển nguyên header khách gửi), nên mọi khách vãng
lai đi qua web dùng chung một khoá là IP container frontend (khách gọi thẳng gateway `:8080` thì được
tính theo IP thật). Khách đã đăng nhập tính theo tài khoản nên không bị ảnh hưởng; nếu khách vãng lai
hay chạm hạn mức thì tăng `CHAT_GUEST_LIMIT`. Header chỉ được tin khi bên gọi chat-service là địa chỉ
nội bộ (loopback, 10/8, 172.16/12, 192.168/16 — tức gateway trong mạng Docker); gọi thẳng cổng 8092
từ IP công khai thì chat-service dùng IP đó, bỏ qua header. Cổng 8092 hiện vẫn mở ra ngoài như các
service khác; người trong mạng LAN gọi thẳng 8092 vẫn giả được IP, và trần `CHAT_DAILY_CAP` là lớp
chặn cuối.

**Chạy nhiều instance.** Bộ đếm hạn mức (`RateLimiter`) nằm trong bộ nhớ của từng instance; chạy
từ 2 instance trở lên thì mỗi instance đếm riêng (hạn mức thực tế nhân lên theo số instance) — khi
đó cần chuyển bộ đếm sang Redis. Hạn mức ngày của A2 cũng đếm trong bộ nhớ như vậy.

## Tiến độ

Xem `docs/tien-do-du-an.md` (service nào xong, luồng sự kiện) và `docs/van-de-ton-dong.md`
(vấn đề đã biết, đã sửa và chưa sửa).
