# Order Service

Giỏ hàng, đơn hàng, tích hợp cổng thanh toán (VNPay/Momo).

- Database riêng: `order_db` (PostgreSQL)
- Sự kiện phát ra: "Đơn hàng thành công" → Inventory Service, Notification Service
- Cấu trúc dự kiến: `src/main/java/...`, `Dockerfile`, `pom.xml`
