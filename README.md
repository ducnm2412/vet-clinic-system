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
│   ├── booking-service/         # đặt lịch khám — booking_db
│   ├── pet-service/             # hồ sơ thú cưng — pet_db
│   ├── product-service/         # sản phẩm, tồn kho — product_db
│   ├── order-service/           # giỏ hàng, đơn hàng, thanh toán — order_db
│   ├── staff-service/           # chấm công — staff_db
│   ├── notification-service/    # gửi email/Zalo qua RabbitMQ
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

## Bước tiếp theo

Mỗi thư mục service hiện chỉ có README mô tả — cần khởi tạo project Spring Boot thật (pom.xml, Application.java, Dockerfile) bên trong khi bắt đầu code, theo đúng lộ trình 8 tuần trong kế hoạch (Eureka + Gateway + Auth trước, Booking song song ở Frontend).
