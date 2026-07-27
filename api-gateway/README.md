# API Gateway

Điểm vào duy nhất của hệ thống: định tuyến request, xác thực token, rate limiting.

- Công nghệ: Spring Cloud Gateway
- Database: không có
- Giao tiếp: route REST đồng bộ tới các service qua Eureka
- Cấu trúc dự kiến: `src/main/java/...`, `application.yml`, `Dockerfile`
