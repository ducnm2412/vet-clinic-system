# Booking Service

Đặt lịch khám online, đặt lịch tại quầy, quản lý khung giờ, trạng thái lịch.

- Database riêng: `booking_db` (PostgreSQL)
- Sự kiện phát ra: "Đặt lịch thành công" → Notification Service (qua RabbitMQ)
- Cấu trúc dự kiến: `src/main/java/...`, `Dockerfile`, `pom.xml`
