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
- `index.html`: một trang, ba tab.

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

### Tab "Giỏ hàng & Đơn hàng"

| Khối | Chức năng | Quyền |
|---|---|---|
| Giỏ hàng | Thêm, sửa số lượng, xoá, xoá sạch; hiện cảnh báo hết hàng theo từng dòng (CN-32) | CUSTOMER |
| Đặt hàng | Form người nhận + địa chỉ, thanh toán COD (CN-33) | CUSTOMER |
| Đơn hàng của tôi | Danh sách, xem chi tiết, huỷ đơn còn chờ xác nhận (CN-35) | CUSTOMER |
| Xử lý đơn | Lọc theo trạng thái; xác nhận → giao hàng → hoàn tất, hoặc huỷ (CN-36) | STAFF/ADMIN |
| Chi tiết đơn & lịch sử | Từng dòng hàng, tổng tiền, và vết chuyển trạng thái | cả hai |

Bảng đơn chỉ hiện nút ứng với bước hợp lệ tiếp theo — luật luồng nằm ở backend, nút chỉ là
gợi ý. Bấm "Xác nhận" là `order-service` phát `order.completed` và `product-service` trừ tồn
kho ngay; sang tab "Sản phẩm & Kho" bấm "Lịch sử" của sản phẩm đó sẽ thấy dòng `SALE`.

Huỷ đơn **đã xác nhận** thì hàng được hoàn lại kho qua sự kiện `order.cancelled` — lịch sử
kho sẽ có thêm dòng `RETURN`.

Muốn thử cả hai phía cùng lúc thì mở thêm một cửa sổ ẩn danh: một bên đăng nhập khách, một
bên đăng nhập admin (token lưu trong `localStorage` nên hai cửa sổ thường sẽ ghi đè nhau).

### Lưu ý khi vừa `docker compose up --build`

API Gateway trả `503` trong khoảng 30 giây đầu: Spring Cloud LoadBalancer cache danh sách
instance từ Eureka theo chu kỳ, phải chờ nó refresh. Đợi rồi tải lại trang, không phải lỗi.
