# Vấn đề tồn đọng

Danh sách nợ kỹ thuật và chức năng còn thiếu, ghi lại để không quên khi quay lại.
Cập nhật lần cuối: 25/08/2026.

Mức độ: 🔴 cần sửa sớm · 🟡 nên sửa · 🟢 chờ phụ thuộc · ⚪ không gấp

---

## product-service

### 🔴 VD-01. Khách vãng lai xem được hàng đã ẩn

`GET /products` là endpoint công khai, nhưng tham số `activeOnly` do client tự truyền
(`ProductController.java:49`). Ai cũng thêm `?activeOnly=false` được.

Đã kiểm chứng bằng dữ liệu thật:

```
Tạo sản phẩm active=false                    -> 201
Khách không token, mặc định                  -> thấy 0 sản phẩm  (đúng)
Khách không token, ?activeOnly=false         -> thấy 1 sản phẩm  (SAI)
```

`active=false` dùng để ngừng bán một mặt hàng, nếu ai cũng xem được thì cờ này vô nghĩa.

**Hướng sửa:** bỏ `activeOnly` khỏi tham số client, thay bằng đọc quyền từ `Authentication` —
có `STAFF`/`ADMIN` mới thấy hàng ẩn, còn lại luôn ép `activeOnly=true`.

### 🟡 VD-02. Không có khoá chống tranh chấp khi sửa tồn kho

`ProductService.adjustStock` và `applySale` đọc `stockQuantity` rồi ghi đè, không có
`@Version` hay `SELECT FOR UPDATE`. Hai thao tác đồng thời sẽ ghi đè nhau và số tồn sai.

Hiện chưa lộ ra vì listener RabbitMQ mặc định chỉ 1 consumer thread nên sự kiện xử lý tuần
tự. Nhưng `POST /products/{id}/stock` thì hai nhân viên bấm cùng lúc là dính ngay.

**Hướng sửa:** thêm `@Version` vào entity `Product` (optimistic locking).

### 🟡 VD-03. Xoá sản phẩm làm mất sạch lịch sử kho

`stock_movements.product_id` có `ON DELETE CASCADE`, nên `DELETE /products/{id}` cuốn theo
toàn bộ vết nhập/xuất — mâu thuẫn với chính mục đích "ghi vết để đối soát".

**Hướng sửa:** chuyển sang soft delete (đặt `active=false`), hoặc chặn xoá sản phẩm đã từng
có giao dịch. Nên quyết trước khi có dữ liệu thật, đổi sau sẽ phải di trú.

### 🟢 VD-04. Chưa có giữ chỗ tồn kho

Kho chỉ trừ khi đơn *hoàn tất*. Hai khách cùng đặt món cuối cùng thì cả hai đều qua được
bước đặt hàng, đến lúc trừ kho mới phát hiện thiếu.

Phải bàn cùng lúc với `order-service` (xem VD-14), không giải riêng trong `product-service`
được.

### ⚪ Khác

- Ảnh sản phẩm mới lưu `imageUrl` dạng chuỗi, chưa có upload file.
- `GET /categories` và `GET /products/{id}/stock-movements` trả toàn bộ, chưa phân trang.
  Ổn ở quy mô hiện tại, sẽ nặng khi lịch sử dài.
- Muốn ẩn/hiện một sản phẩm phải gọi `PUT` toàn bộ, chưa có endpoint sửa nhanh cờ `active`.

---

## order-service

### 🟡 VD-14. Kiểm tra tồn kho lúc checkout không có tính nguyên tử

`checkout` hỏi `product-service` xem còn đủ hàng không, nhưng giữa lúc hỏi và lúc nhân viên
xác nhận đơn (mới thực sự trừ kho) thì hàng có thể đã bán hết cho người khác. Khi đó
`product-service` ghi log lỗi và bỏ qua, đơn vẫn ở `CONFIRMED` nhưng kho không trừ.

Đây là mặt còn lại của VD-04 (chưa có giữ chỗ tồn kho). Giải đúng thì cần API `reserve` /
`release` bên `product-service`, hoặc để `confirm` gọi đồng bộ sang `product-service` và
thất bại thì không cho chuyển trạng thái.

### 🟡 VD-15. CN-34 mới dừng ở COD

`PaymentMethod` chỉ có `COD`. Tích hợp VNPay/Momo cần tài khoản merchant và một URL công khai
để cổng thanh toán gọi ngược lại (IPN callback) — `localhost` thì họ không gọi tới được.

Khi làm: thêm bảng `payments`, tách một lớp adapter cho từng cổng, và thêm trạng thái chờ
thanh toán vào đầu luồng.

### ⚪ Khác

- Đơn ở `SHIPPING` không huỷ được. Thực tế khách vẫn từ chối nhận hàng COD — khi đó cần
  luồng "giao thất bại" để hoàn kho, hiện chưa có.
- Chưa có endpoint sửa địa chỉ giao hàng của đơn còn `PENDING`.
- Giỏ hàng không tự dọn sản phẩm đã bị xoá; response đánh dấu `available=false` để giao diện
  hiển thị, nhưng dòng đó vẫn nằm trong giỏ cho tới khi khách tự xoá.

---

## auth-service

### 🔴 VD-05. CN-07 — refresh token phát ra nhưng không dùng được

`login` trả về `refreshToken` nhưng **không có endpoint nào tiêu thụ nó**. Access token sống
15 phút, nên cứ 15 phút là người dùng bị đăng xuất và phải đăng nhập lại.

**Hướng sửa:** thêm `POST /auth/refresh`. Nên làm sớm — để đến khi frontend thật đã gọi hàng
chục API thì sửa sẽ phiền hơn nhiều.

### 🟡 VD-06. CN-08 — không khoá được tài khoản

`UserStatus` khai báo sẵn giá trị `LOCKED` nhưng **không code nào set giá trị đó**, cũng
không đếm số lần đăng nhập sai.

### 🟡 VD-07. Không gửi lại được email xác thực

Token xác thực hết hạn sau 24 giờ. Quá hạn là tài khoản kẹt vĩnh viễn ở `INACTIVE`, phải sửa
tay trong DB. Chưa có endpoint gửi lại.

### 🟡 VD-08. Không có đổi mật khẩu / quên mật khẩu

Chưa có `POST /auth/change-password` lẫn luồng reset qua email.

### 🟢 VD-09. Email chỉ ghi ra log

`AuthService.sendVerificationEmail` mới `log.info` (có sẵn `// TODO` trong code). Chờ
`notification-service` để publish sự kiện và gửi mail thật.

---

## profile-service

### 🟡 VD-10. Ranh giới `Pet` chồng lấn với `pet-service`

Entity `Pet` đang nằm trong `profile-service` (thuộc hồ sơ khách hàng), trong khi roadmap có
`pet-service` riêng. Đã thống nhất trong `phan-tich-chuc-nang.md` mục 2.4: thu hẹp
`pet-service` thành **bệnh án & đơn thuốc**. Cần bám đúng ranh giới này khi làm tới nơi.

---

## booking-service

### 🔴 VD-16. Lịch hẹn không cho biết bác sĩ nào khám

`AppointmentRequest` chỉ nhận `{petId, date, startTime, reason}` — khách không chọn được bác
sĩ, hệ thống tự gán slot. Bản thân điều đó là một quyết định hợp lệ.

Vấn đề nằm ở chiều ngược lại: **`AppointmentResponse` và `AppointmentDetailResponse` không
trả về `doctorUserId`**, chỉ có `slotId`. Trong khi đó `GET /booking/appointments` lại *lọc*
được theo `doctorUserId`.

Hệ quả:

```
Lọc lịch theo bác sĩ        -> lam duoc
Hiện tên bác sĩ trên lịch   -> KHONG lam duoc
```

Nên không màn hình nào — của khách, của bác sĩ hay của quản trị — nói được ai phụ trách ca
khám. Bác sĩ mở `/booking/appointments/doctor/me` thì biết đó là ca của mình, nhưng lễ tân
nhìn danh sách chung thì không phân biệt được.

**Hướng sửa:** thêm `doctorUserId` (và tên bác sĩ nếu tiện) vào hai response trên. Dữ liệu đã
có sẵn qua `slotId`, chỉ là chưa trả ra.

### 🟡 VD-17. Bệnh án chỉ tra được theo lịch hẹn, không theo thú cưng

Bệnh án truy cập qua `GET /booking/appointments/{id}/medical-record`. Không có endpoint nào
lấy lịch sử khám của **một thú cưng** qua nhiều lần hẹn.

Đây đúng là chức năng CN-24 "tra cứu lịch sử khám bệnh" và là thứ bác sĩ cần nhất khi khám:
con vật này trước đây bị gì, đã dùng thuốc nào.

**Hướng sửa:** thêm `GET /booking/pets/{petId}/medical-records`.

---

## Toàn hệ thống

### 🟡 VD-11. CN-22 và CN-39 chồng lấn — chưa chốt

- `CN-22` (booking-service): "quản lý khung giờ làm việc, cấu hình ca trực"
- `CN-39` (staff-service): "xếp ca trực cho bác sĩ, nhân viên theo tuần"

Hai chức năng cùng nói về *ca trực của bác sĩ*. Không chốt thì sẽ có hai nguồn sự thật và
booking không biết hỏi ai để biết bác sĩ có rảnh không.

**Đề xuất:** lịch làm việc (kế hoạch) thuộc `booking-service` vì nó cần tính slot trống theo
thời gian thực; `staff-service` chỉ giữ chấm công thực tế (check-in/out, tổng giờ công).

### 🟡 VD-12. Test tích hợp cần Postgres thật

`auth-service` và `profile-service` không có `src/test/resources/application.yml` riêng, nên
`@SpringBootTest` dùng thẳng `application.yml` chính và cần DB thật đúng cổng. Chạy `mvn test`
trần sẽ hỏng nếu không set biến môi trường.

`product-service` đã đỡ hơn một phần: test controller tắt listener RabbitMQ nên không cần
broker, nhưng vẫn cần Postgres.

**Hướng sửa:** dùng Testcontainers, hoặc thêm profile test trỏ sẵn đúng cổng.

### ⚪ VD-13. Gateway trả 503 khoảng 30 giây sau khi rebuild

Spring Cloud LoadBalancer cache danh sách instance từ Eureka theo chu kỳ. Không phải lỗi,
nhưng dễ làm mất công debug nhầm. Đã ghi trong `frontend/README.md`.

### 🟡 VD-18. Bảy mảng giao diện quản trị chưa có API

Rà khi dựng frontend Next.js. Các màn hình dưới đây không có endpoint nào phục vụ:

| Màn hình | Thiếu gì |
|---|---|
| Danh sách tài khoản | Chỉ có `POST` và `DELETE /admin/users`, không có `GET` |
| Danh sách khách hàng | Chỉ có `GET /profile/customer/by-id/{id}`, không có endpoint liệt kê |
| Số liệu tổng quan | Không có API thống kê nào (doanh thu, số ca hôm nay, số khách) |
| Biểu đồ báo cáo | `reporting-service` chưa tồn tại |
| Chấm công | `staff-service` chưa tồn tại |
| Lịch sử khám của thú cưng | Xem VD-17 |
| Thông báo trong ứng dụng | `notification-service` không có REST endpoint |

Frontend đang để placeholder có đánh dấu `TODO` và tầng mock riêng (`lib/api/mock/`), không
nhúng dữ liệu giả vào component. Khi API có thật thì chỉ thay tầng đó.

Hai mục đầu bảng là rẻ nhất và chặn nhiều màn hình nhất — nên làm trước.

### 🟡 VD-19. Service goi nhau luc khoi dong tra 500 thay vi 503

Phat hien khi chay kiem chung tu dong ngay sau `docker compose up`.

`POST /cart/items` tra **500**. Log `order-service`:

```
feign.RetryableException -> java.net.ConnectException
```

`order-service` goi `product-service` de lay gia va kiem ton kho — dung nhu thiet ke
"khong tin so lieu client gui len". Nhung neu `product-service` chua dang ky xong voi
Eureka thi loi goi do chet, va nguoi dung nhan **500**.

VD-13 moi noi gateway tra 503 khoang 30 giay sau khi khoi dong lai. Thuc te **cac
service goi nhau cung co cua so tuong tu**, va o do loi hien ra duoi dang 500.

Voi nguoi dung, 500 la "he thong hong" con 503 la "cho chut" — hai thong diep rat khac
nhau. Frontend dien giai 503 thanh "Dich vu dang khoi dong, thu lai sau vai giay",
nhung 500 thi roi vao thong bao loi chung.

**Huong sua:** `ProductClient` bat `FeignException` va nem ra loi map sang 503, hoac
them retry co backoff. Cung nen ap dung cho moi cho service goi cheo nhau.
