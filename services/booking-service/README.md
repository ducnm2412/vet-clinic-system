# Booking Service

Đặt lịch khám online, đặt lịch tại quầy, quản lý khung giờ, trạng thái lịch.

- Database riêng: `booking_db` (PostgreSQL)
- Sự kiện phát ra: "Đặt lịch thành công" → Notification Service (qua RabbitMQ)
- Cấu trúc dự kiến: `src/main/java/...`, `Dockerfile`, `pom.xml`

Bệnh án và đơn thuốc **không còn ở đây** — đã chuyển sang `pet-service` ngày 25/09/2026
(VD-10 chặng 2). Hai bảng cũ còn nằm lại trong `booking_db` dưới tên
`*_moved_to_pet_service_backup` để đối chiếu.
