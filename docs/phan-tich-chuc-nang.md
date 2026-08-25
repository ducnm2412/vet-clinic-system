# Bảng phân tích chức năng hệ thống

**Đề tài:** Website quản lý & đặt lịch khám bệnh thú y kết hợp bán hàng trực tuyến
**Kiến trúc:** Microservices (Spring Boot + Spring Cloud), Database per Service
**Ngày lập:** 23/08/2026

---

## 1. Bảng tác nhân (Actor)

| Mã | Tác nhân | Mô tả | Cách tạo tài khoản |
|---|---|---|---|
| AC-01 | Khách hàng (CUSTOMER) | Chủ thú cưng: đặt lịch khám, mua hàng, quản lý hồ sơ thú cưng | Tự đăng ký + xác minh email |
| AC-02 | Bác sĩ thú y (DOCTOR) | Khám bệnh, lập hồ sơ bệnh án, kê đơn, chấm công | Admin tạo (`POST /admin/users`) |
| AC-03 | Nhân viên (STAFF) | Lễ tân/kho: đặt lịch tại quầy, xử lý đơn hàng, chấm công | Admin tạo (`POST /admin/users`) |
| AC-04 | Quản trị viên (ADMIN) | Quản lý tài khoản, danh mục, xem báo cáo doanh thu | Seed sẵn khi khởi động hệ thống |
| AC-05 | Hệ thống ngoài | Cổng thanh toán VNPay/Momo, dịch vụ Email/Zalo OA | — |

---

## 2. Bảng phân rã chức năng hệ thống

Chú thích trạng thái: ✅ Đã hiện thực · ⚠️ Hiện thực một phần · ⏳ Chưa hiện thực

### 2.1. Module Xác thực & Phân quyền (`auth-service` — `auth_db`)

| Mã CN | Tên chức năng | Tác nhân | Mô tả xử lý | Đầu vào | Đầu ra | Trạng thái |
|---|---|---|---|---|---|---|
| CN-01 | Đăng ký tài khoản | AC-01 | Tạo user `status=INACTIVE`, gán role CUSTOMER, hash BCrypt, sinh token xác minh (hạn 24 giờ) | Họ tên, email, mật khẩu | Thông báo yêu cầu xác minh email (chưa cấp JWT) | ✅ |
| CN-02 | Xác minh email | AC-01 | Kiểm tra token dùng-một-lần còn hạn → chuyển `status=ACTIVE` | Token xác minh | Kết quả kích hoạt | ✅ |
| CN-03 | Đăng nhập | AC-01→04 | Đối chiếu BCrypt, chặn tài khoản chưa ACTIVE, phát JWT (access 15 phút, refresh 7 ngày) | Email, mật khẩu | `accessToken`, `refreshToken` | ✅ |
| CN-04 | Xem thông tin phiên đăng nhập | AC-01→04 | Giải mã JWT lấy email → truy vấn hồ sơ user | JWT | Thông tin user + danh sách role | ✅ |
| CN-05 | Tạo tài khoản Bác sĩ/Nhân viên | AC-04 | Tạo tài khoản `status=ACTIVE` ngay, chỉ cho phép role DOCTOR/STAFF | Họ tên, email, mật khẩu, role | Thông báo tạo thành công | ✅ |
| CN-06 | Phân quyền truy cập (RBAC) | Hệ thống | Filter JWT stateless chặn theo route và role, không truy vấn DB mỗi request | JWT trong header | Cho phép / 401 / 403 | ✅ |
| CN-07 | Làm mới access token | AC-01→04 | Dùng refresh token cấp lại access token khi hết hạn | `refreshToken` | `accessToken` mới | ⏳ |
| CN-08 | Khoá / mở khoá tài khoản | AC-04 | Chuyển `status=LOCKED`, chặn đăng nhập | ID tài khoản | Kết quả cập nhật | ⏳ |

### 2.2. Module Hồ sơ người dùng & Thú cưng (`profile-service` — `profile_db`)

| Mã CN | Tên chức năng | Tác nhân | Mô tả xử lý | Đầu vào | Đầu ra | Trạng thái |
|---|---|---|---|---|---|---|
| CN-09 | Xem/Cập nhật hồ sơ khách hàng | AC-01 | Đọc & sửa thông tin cá nhân gắn với `userId` trong JWT | Số điện thoại, ngày sinh, giới tính… | Hồ sơ khách hàng | ✅ |
| CN-10 | Quản lý sổ địa chỉ | AC-01 | Thêm / sửa / xoá / liệt kê địa chỉ giao hàng | Thông tin địa chỉ | Danh sách địa chỉ | ✅ |
| CN-11 | Quản lý hồ sơ thú cưng | AC-01 | Thêm / sửa / xoá / liệt kê thú cưng (tên, loài, giống, giới tính, cân nặng…) | Thông tin thú cưng | Danh sách thú cưng | ✅ |
| CN-12 | Xem/Cập nhật hồ sơ bác sĩ | AC-02 | Chuyên khoa, kinh nghiệm, mô tả giới thiệu | Thông tin chuyên môn | Hồ sơ bác sĩ | ✅ |
| CN-13 | Quản lý chứng chỉ hành nghề | AC-02 | Thêm / sửa / xoá / liệt kê chứng chỉ của chính bác sĩ | Số hiệu, nơi cấp, hạn | Danh sách chứng chỉ | ✅ |
| CN-14 | Tra cứu danh sách bác sĩ công khai | Public | Trả danh sách bác sĩ rút gọn cho trang đặt lịch (không cần đăng nhập) | — | Danh sách bác sĩ | ✅ |
| CN-15 | Xem/Cập nhật hồ sơ nhân viên | AC-03 | Thông tin cá nhân, vị trí công việc | Thông tin nhân viên | Hồ sơ nhân viên | ✅ |
| CN-16 | Xem toàn bộ hồ sơ nhân viên | AC-04 | Liệt kê nhân sự phục vụ quản trị | — | Danh sách nhân viên | ✅ |

### 2.3. Module Đặt lịch khám (`booking-service` — `booking_db`)

| Mã CN | Tên chức năng | Tác nhân | Mô tả xử lý | Trạng thái |
|---|---|---|---|---|
| CN-17 | Tra cứu khung giờ trống | AC-01 | Hiển thị lịch làm việc và slot còn trống theo bác sĩ/ngày | ⏳ |
| CN-18 | Đặt lịch khám online | AC-01 | Chọn thú cưng, dịch vụ, bác sĩ, khung giờ → tạo lịch hẹn, phát sự kiện lên RabbitMQ | ⏳ |
| CN-19 | Đặt lịch tại quầy | AC-03 | Lễ tân tạo lịch hộ khách vãng lai | ⏳ |
| CN-20 | Huỷ / đổi lịch hẹn | AC-01, AC-03 | Cập nhật trạng thái lịch, giải phóng khung giờ | ⏳ |
| CN-21 | Quản lý trạng thái lịch khám | AC-02, AC-03 | Luồng: Chờ xác nhận → Đã xác nhận → Đang khám → Hoàn tất / Đã huỷ | ⏳ |
| CN-22 | Quản lý khung giờ làm việc | AC-04 | Cấu hình ca trực, số slot tối đa mỗi khung giờ | ⏳ |

### 2.4. Module Bệnh án & Đơn thuốc (`pet-service` — `pet_db`)

| Mã CN | Tên chức năng | Tác nhân | Mô tả xử lý | Trạng thái |
|---|---|---|---|---|
| CN-23 | Lập hồ sơ bệnh án | AC-02 | Ghi triệu chứng, chẩn đoán, kết quả điều trị theo mỗi lượt khám | ⏳ |
| CN-24 | Tra cứu lịch sử khám bệnh | AC-01, AC-02 | Xem toàn bộ lượt khám trước đây của một thú cưng | ⏳ |
| CN-25 | Kê đơn thuốc | AC-02 | Lập đơn thuốc gắn với bệnh án, liều dùng, số lượng | ⏳ |
| CN-26 | Nhắc lịch tái khám / tiêm phòng | Hệ thống | Sinh sự kiện nhắc lịch gửi sang Notification Service | ⏳ |

> **Ghi chú thiết kế:** entity `Pet` hiện nằm trong `profile-service` (thuộc hồ sơ khách hàng). Do đó `pet-service` nên thu hẹp phạm vi thành **bệnh án & đơn thuốc**, tránh trùng lặp quyền sở hữu dữ liệu giữa hai service.

### 2.5. Module Sản phẩm & Tồn kho (`product-service` — `product_db`)

| Mã CN | Tên chức năng | Tác nhân | Mô tả xử lý | Trạng thái |
|---|---|---|---|---|
| CN-27 | Quản lý danh mục sản phẩm | AC-04 | CRUD nhóm hàng (thức ăn, thuốc, phụ kiện); chặn xoá danh mục còn sản phẩm | ✅ |
| CN-28 | Quản lý sản phẩm | AC-04 | CRUD sản phẩm: SKU, tên, giá, ảnh, mô tả, đơn vị tính | ✅ |
| CN-29 | Tìm kiếm & xem chi tiết sản phẩm | AC-01 | Lọc theo danh mục, khoảng giá, từ khoá; phân trang; công khai không cần đăng nhập | ✅ |
| CN-30 | Quản lý tồn kho | AC-03, AC-04 | Nhập kho, điều chỉnh sau kiểm kê, lịch sử biến động, cảnh báo sắp hết hàng | ✅ |
| CN-31 | Tự động trừ tồn kho | Hệ thống | Lắng nghe `order.completed` từ RabbitMQ → giảm tồn; chống xử lý trùng message | ⚠️ Consumer đã sẵn sàng, chờ `order-service` phát sự kiện |

> **Ghi chú thiết kế:** `products.stock_quantity` giữ tồn hiện tại để lọc/hiển thị nhanh, còn
> `stock_movements` ghi vết từng lần biến động kèm `quantity_after` để đối soát. Đổi tồn kho
> bắt buộc đi qua endpoint nhập/điều chỉnh, `PUT /products/{id}` không sửa được tồn.
>
> Service **không dùng prefix `/admin`** vì gateway đã dành đường đó cho `auth-service`;
> phân quyền theo HTTP method trên cùng prefix tài nguyên (`GET` công khai, `POST/PUT/DELETE`
> cần ADMIN, riêng thao tác kho cho STAFF/ADMIN).

### 2.6. Module Đơn hàng & Thanh toán (`order-service` — `order_db`)

| Mã CN | Tên chức năng | Tác nhân | Mô tả xử lý | Trạng thái |
|---|---|---|---|---|
| CN-32 | Quản lý giỏ hàng | AC-01 | Thêm / sửa số lượng / xoá sản phẩm khỏi giỏ | ⏳ |
| CN-33 | Đặt hàng (checkout) | AC-01 | Chọn địa chỉ giao hàng, phương thức thanh toán, tạo đơn | ⏳ |
| CN-34 | Thanh toán trực tuyến | AC-01, AC-05 | Tích hợp VNPay/Momo, xử lý callback IPN cập nhật trạng thái thanh toán | ⏳ |
| CN-35 | Theo dõi trạng thái đơn hàng | AC-01 | Luồng: Chờ thanh toán → Đã thanh toán → Đang giao → Hoàn tất / Đã huỷ | ⏳ |
| CN-36 | Xử lý đơn hàng | AC-03 | Xác nhận, đóng gói, cập nhật trạng thái giao hàng | ⏳ |
| CN-37 | Phát sự kiện đơn hàng thành công | Hệ thống | Publish RabbitMQ → Product Service (trừ kho), Notification, Reporting | ⏳ |

### 2.7. Module Nhân sự & Chấm công (`staff-service` — `staff_db`)

| Mã CN | Tên chức năng | Tác nhân | Mô tả xử lý | Trạng thái |
|---|---|---|---|---|
| CN-38 | Chấm công vào/ra | AC-02, AC-03 | Ghi nhận check-in / check-out kèm mốc thời gian | ⏳ |
| CN-39 | Quản lý lịch làm việc | AC-04 | Xếp ca trực cho bác sĩ, nhân viên theo tuần | ⏳ |
| CN-40 | Tổng hợp giờ công | AC-04 | Tính tổng giờ làm, số ngày công theo kỳ lương | ⏳ |
| CN-41 | Phát sự kiện chấm công | Hệ thống | Publish RabbitMQ → Reporting Service | ⏳ |

### 2.8. Module Thông báo (`notification-service` — không có DB riêng)

| Mã CN | Tên chức năng | Tác nhân | Mô tả xử lý | Trạng thái |
|---|---|---|---|---|
| CN-42 | Gửi email xác minh tài khoản | Hệ thống | Nhận sự kiện đăng ký → gửi mail chứa link xác minh | ⚠️ Hiện chỉ ghi log ra console tại `auth-service` |
| CN-43 | Thông báo đặt lịch thành công | Hệ thống | Consume sự kiện booking → gửi Email/Zalo OA cho khách | ⏳ |
| CN-44 | Thông báo đơn hàng | Hệ thống | Consume sự kiện order → xác nhận đơn, cập nhật giao hàng | ⏳ |
| CN-45 | Nhắc lịch tái khám / tiêm phòng | Hệ thống | Gửi nhắc theo lịch định kỳ | ⏳ |

### 2.9. Module Báo cáo & Thống kê (`reporting-service` — read model)

| Mã CN | Tên chức năng | Tác nhân | Mô tả xử lý | Trạng thái |
|---|---|---|---|---|
| CN-46 | Báo cáo doanh thu | AC-04 | Tổng hợp doanh thu bán hàng + dịch vụ khám theo ngày/tháng/quý | ⏳ |
| CN-47 | Thống kê lịch khám | AC-04 | Số lượt khám, tỉ lệ huỷ lịch, hiệu suất theo bác sĩ | ⏳ |
| CN-48 | Báo cáo chấm công | AC-04 | Tổng hợp giờ công toàn bộ nhân sự | ⏳ |
| CN-49 | Dashboard quản trị | AC-04 | Bảng điều khiển tổng hợp các chỉ số vận hành | ⏳ |

### 2.10. Chức năng hạ tầng (nền tảng kỹ thuật)

| Mã CN | Tên chức năng | Thành phần | Mô tả | Trạng thái |
|---|---|---|---|---|
| CN-50 | Đăng ký & khám phá dịch vụ | `eureka-server` | Các service tự đăng ký, tra cứu nhau qua tên logic thay vì IP/port | ✅ |
| CN-51 | Định tuyến tập trung | `api-gateway` | Một điểm vào duy nhất (cổng 8080), route `lb://` theo Eureka | ✅ (mới khai báo route `auth-service`) |
| CN-52 | Quản lý phiên bản schema CSDL | Flyway | Migration có phiên bản, Hibernate chạy `ddl-auto: validate` | ✅ (auth, profile) |
| CN-53 | Giao tiếp bất đồng bộ | RabbitMQ | Truyền sự kiện giữa các service, giảm phụ thuộc trực tiếp | ⏳ |
| CN-54 | Đóng gói & triển khai | Docker Compose | Khởi chạy toàn bộ hệ thống + 7 PostgreSQL + RabbitMQ + Redis | ✅ (khung) |

---

## 3. Ma trận chức năng – tác nhân

| Mã CN | Khách hàng | Bác sĩ | Nhân viên | Admin | Hệ thống |
|---|:---:|:---:|:---:|:---:|:---:|
| CN-01, CN-02 | X | | | | |
| CN-03, CN-04 | X | X | X | X | |
| CN-05, CN-08 | | | | X | |
| CN-06 | | | | | X |
| CN-09 → CN-11 | X | | | | |
| CN-12, CN-13 | | X | | | |
| CN-14 | X | X | X | X | |
| CN-15 | | | X | | |
| CN-16 | | | | X | |
| CN-17, CN-18, CN-20 | X | | X | | |
| CN-19 | | | X | | |
| CN-21 | | X | X | | |
| CN-22 | | | | X | |
| CN-23, CN-25 | | X | | | |
| CN-24 | X | X | | | |
| CN-26, CN-31, CN-37, CN-41 → CN-45 | | | | | X |
| CN-27, CN-28 | | | X | X | |
| CN-29, CN-32 → CN-35 | X | | | | |
| CN-30, CN-36 | | | X | | |
| CN-38 | | X | X | | |
| CN-39, CN-40, CN-46 → CN-49 | | | | X | |

---

## 4. Tổng kết mức độ hoàn thành

| Nhóm chức năng | Tổng số | Đã hiện thực | Tỉ lệ |
|---|---|---|---|
| Xác thực & Phân quyền | 8 | 6 | 75% |
| Hồ sơ người dùng & Thú cưng | 8 | 8 | 100% |
| Đặt lịch khám | 6 | 0 | 0% |
| Bệnh án & Đơn thuốc | 4 | 0 | 0% |
| Sản phẩm & Tồn kho | 5 | 0 | 0% |
| Đơn hàng & Thanh toán | 6 | 0 | 0% |
| Nhân sự & Chấm công | 4 | 0 | 0% |
| Thông báo | 4 | 0 | 0% |
| Báo cáo & Thống kê | 4 | 0 | 0% |
| Hạ tầng kỹ thuật | 5 | 4 | 80% |
| **Tổng cộng** | **54** | **18** | **≈33%** |

---

## 5. Các quyết định thiết kế đáng lưu ý

1. **CN-01 không cấp JWT ngay sau đăng ký.** `JwtAuthFilter` xác thực stateless (không truy vấn DB ở mỗi request), nên nếu cấp token ngay khi đăng ký thì ràng buộc xác minh email sẽ vô hiệu — token vẫn dùng được dù tài khoản chưa kích hoạt.
2. **`profile-service` tự xác thực JWT bằng chung `JWT_SECRET`** thay vì gọi ngược `auth-service`. Ưu điểm: giảm phụ thuộc giữa các service, không tạo điểm nghẽn. Đánh đổi: thay đổi role chỉ có hiệu lực ở lần đăng nhập kế tiếp (token cũ còn hạn 15 phút).
3. **Tài khoản Bác sĩ/Nhân viên chỉ do Admin tạo**, không có form tự đăng ký công khai — tránh nguy cơ leo thang đặc quyền khi client tự khai role.
4. **Lỗi đăng nhập không phân biệt nguyên nhân** (sai email / sai mật khẩu / chưa xác minh) — cùng trả HTTP 401 với thông báo chung, chống tấn công dò tài khoản (user enumeration).
5. **Flyway là nguồn sự thật duy nhất cho schema**, Hibernate chạy `ddl-auto: validate` — bảo đảm cấu trúc CSDL có phiên bản và tái lập được ở mọi môi trường.
