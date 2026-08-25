# Frontend

Giao diện khách hàng và admin.

- Công nghệ dự kiến: React.js / Next.js
- Giao tiếp: gọi REST API qua API Gateway
- Cấu trúc dự kiến: `pages/` hoặc `app/`, `components/`, `Dockerfile`

## `test-ui/` — trang test API tạm thời

Chưa có frontend thật, nên `test-ui/` đóng vai trò công cụ thử API bằng tay trong lúc phát
triển backend. Đây **không phải giao diện sản phẩm cuối** và sẽ bị thay khi làm React/Next.js.

- Chạy tại http://localhost:3000 (đi kèm `docker compose up`)
- `server.js`: server Node thuần, không phụ thuộc npm package nào. Serve `index.html` và
  proxy mọi `/api-proxy/*` sang API Gateway để tránh lỗi CORS.
- `index.html`: một trang, hai tab.

### Tab "Tài khoản & Hồ sơ"

Đăng ký → xác thực email → đăng nhập → xem `/auth/me` → hồ sơ khách hàng → danh sách bác sĩ.

Access token lưu trong `localStorage` và tự đính vào các request cần xác thực. Thanh trạng
thái phía trên hiển thị token rút gọn cùng vai trò đọc được từ JWT.

Chưa có `notification-service` nên email xác thực không gửi đi đâu. Lấy link trong log:

```powershell
docker compose logs auth-service | Select-String "Verification link"
```

### Tab "Sản phẩm & Kho"

| Khối | Chức năng |
|---|---|
| Danh mục | Tạo, liệt kê, xoá (CN-27) |
| Tạo sản phẩm | Form đầy đủ, chọn danh mục từ danh sách đã nạp (CN-28) |
| Tra cứu sản phẩm | Lọc theo từ khoá/danh mục/khoảng giá, phân trang, bảng kết quả (CN-29) |
| Nhập / điều chỉnh kho | IMPORT, ADJUSTMENT, RETURN kèm ghi chú (CN-30) |
| Cảnh báo sắp hết hàng | Danh sách tồn ≤ ngưỡng (CN-30) |
| Lịch sử biến động kho | Toàn bộ biến động của một sản phẩm, có cả dòng `SALE` sinh từ RabbitMQ |

Tra cứu không cần đăng nhập. Tạo/sửa/xoá cần `ADMIN`, thao tác kho cần `STAFF` hoặc `ADMIN`
— đăng nhập bằng tài khoản admin ở tab đầu trước khi thử.

Bảng sản phẩm có nút tắt "Nhập kho" và "Lịch sử" để nhảy thẳng sang khối tương ứng, khỏi
phải copy id thủ công.

### Lưu ý khi vừa `docker compose up --build`

API Gateway trả `503` trong khoảng 30 giây đầu: Spring Cloud LoadBalancer cache danh sách
instance từ Eureka theo chu kỳ, phải chờ nó refresh. Đợi rồi tải lại trang, không phải lỗi.
