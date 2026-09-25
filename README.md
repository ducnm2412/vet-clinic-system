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

## Tiến độ

Xem `docs/tien-do-du-an.md` (service nào xong, luồng sự kiện) và `docs/van-de-ton-dong.md`
(vấn đề đã biết, đã sửa và chưa sửa).
