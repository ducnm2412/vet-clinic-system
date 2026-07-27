# Notification Service

Worker lắng nghe RabbitMQ, gửi email/Zalo OA khi có lịch, đơn hàng, nhắc lịch tái khám.

- Database riêng: không có (đọc sự kiện qua broker)
- Công nghệ: Spring Mail (JavaMailSender), Zalo OA API, Spring AMQP
- Cấu trúc dự kiến: `src/main/java/...`, `Dockerfile`, `pom.xml`
