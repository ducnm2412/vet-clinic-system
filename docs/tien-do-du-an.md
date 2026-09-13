# Tiến độ dự án Vet Clinic System

**Kiến trúc:** Microservices (Spring Boot + Spring Cloud), Database per Service
**Thời gian:** 27/07/2026 → 09/09/2026 · 27 commit
**Cập nhật lần cuối:** 13/09/2026

Tài liệu này ghi lại **những gì đã làm được**. Bảng phân tích chức năng đầy đủ nằm ở
[phan-tich-chuc-nang.md](phan-tich-chuc-nang.md), nợ kỹ thuật ở
[van-de-ton-dong.md](van-de-ton-dong.md).

---

## 1. Tổng quan

| Hạng mục | Số lượng |
|---|---|
| Service nghiệp vụ đã hiện thực | **8** / 10 |
| Service chưa viết dòng nào | 2 (`pet`, `staff`) |
| File Java (main) | 234 |
| File test | 35 |
| Endpoint REST | 75 |
| Luồng sự kiện RabbitMQ | 9 |
| Database PostgreSQL | 6 |

---

## 2. Các service đã hiện thực

| Service | Cổng | Database | main/test | Migration | Endpoint |
|---|---|---|---|---|---|
| `auth-service` | 8081 | `auth_db` | 32 / 5 | V1–V3 | 6 |
| `profile-service` | 8083 | `profile_db` | 45 / 10 | V1 | 24 |
| `product-service` | 8084 | `product_db` | 33 / 4 | V1 | 13 |
| `order-service` | 8085 | `order_db` | 42 / 3 | V1 | 16 |
| `booking-service` | 8086 | `booking_db` | 53 / 8 | V1–V3 | 13 |
| `payment-service` | 8087 | `payment_db` | 24 / 3 | V1–V3 | 3 |
| `notification-service` | 8088 | — | 5 / 2 | — | 0 |
| `reporting-service` | 8089 | — | 12 / 2 | — | 3 |

Hạ tầng đi kèm: `api-gateway` (8080), `eureka-server` (8761), `frontend` Next.js (3000),
RabbitMQ (5672 / 15672), Redis (6379), MailHog (1025 / 8025).

---

## 3. Chức năng từng service

### 3.1. `auth-service` — tài khoản và phân quyền

Đăng ký, xác minh email bằng token, đăng nhập, xem thông tin phiên. Endpoint
`/admin/users` cho phép `ADMIN` tạo và xoá tài khoản Bác sĩ / Nhân viên.

Là nơi **duy nhất** phát hành JWT. Sáu service còn lại tự xác thực chữ ký bằng
`JWT_SECRET` dùng chung, không gọi ngược lại `auth-service` — tránh biến nó thành
điểm nghẽn của toàn hệ thống.

### 3.2. `profile-service` — hồ sơ người dùng

Nhiều endpoint nhất (24). Ba loại hồ sơ tách riêng:

- **Khách hàng** — thông tin cá nhân, sổ địa chỉ, hồ sơ thú cưng
- **Bác sĩ** — thông tin chuyên môn, chứng chỉ hành nghề
- **Nhân viên** — thông tin nhân sự

`GET /profile/doctors` là endpoint công khai để tra cứu danh sách bác sĩ.

### 3.3. `product-service` — sản phẩm và tồn kho

CRUD danh mục và sản phẩm, tìm kiếm công khai có phân trang và lọc, nhập kho, điều
chỉnh sau kiểm kê, lịch sử biến động, cảnh báo sắp hết hàng.

**Thiết kế tồn kho:** `products.stock_quantity` giữ tồn hiện tại để lọc và hiển thị
nhanh; `stock_movements` ghi vết từng lần biến động kèm `quantity_after` để đối soát.
Mọi thay đổi tồn đều phải đi qua endpoint kho để có vết, không sửa trực tiếp khi cập
nhật sản phẩm.

### 3.4. `order-service` — giỏ hàng và đơn hàng

Giỏ hàng, checkout, khách theo dõi đơn của mình. Nhánh `/orders/manage/**` dành cho
nhân viên: xác nhận → giao hàng → hoàn tất, hoặc huỷ.

**Ba quyết định thiết kế đáng nhớ:**

1. Giá và tồn kho **luôn hỏi `product-service`** lúc checkout, không bao giờ tin con số
   client gửi lên.
2. Đơn **chụp lại** giá, tên sản phẩm và địa chỉ người nhận tại thời điểm đặt. Shop đổi
   giá hay khách sửa sổ địa chỉ về sau đều không làm thay đổi đơn cũ.
3. Kho bị trừ **lúc nhân viên xác nhận đơn**, không phải lúc đặt hay lúc giao xong — nếu
   đợi tới lúc giao xong thì hàng đã hứa cho đơn này vẫn bán tiếp được cho khách khác.

Đường dẫn quản trị đặt ở `/orders/manage/**` chứ không phải `/admin/**` vì gateway đã
dành `/admin/**` cho `auth-service`.

### 3.5. `booking-service` — lịch khám và bệnh án

Đặt lịch, xem lịch theo vai trò (khách / bác sĩ / quản trị), huỷ, đổi trạng thái, lập
bệnh án, kê đơn thuốc, tra cứu khung giờ trống. Có scheduler tự sinh khung giờ.

Migration `V2` đặt **partial unique index** trên khung giờ đang hoạt động để hai người
không đặt trùng một slot.

### 3.6. `payment-service` — thu tiền đơn thuốc

Ba endpoint, chỉ `STAFF` / `ADMIN`: xem danh sách đơn thuốc chờ thu tiền, nhập số tiền,
xác nhận đã thu tiền mặt.

Đây là thanh toán **tại quầy cho đơn thuốc**, thuộc luồng khám bệnh — không phải thanh
toán trực tuyến cho đơn hàng thương mại điện tử (CN-34 vẫn chưa làm).

### 3.7. `notification-service` — gửi email

**Không có endpoint nào**, thuần tiêu thụ sự kiện. Nghe `user.registered` và gửi email
xác minh tài khoản. Dev dùng MailHog, chuyển sang SMTP thật chỉ cần đổi biến môi trường
`MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD`, không phải sửa code hay build lại image.

### 3.8. `reporting-service` — báo cáo cho quản trị viên

CN-46 doanh thu theo ngày/tháng/quý, CN-47 thống kê lịch khám (tỉ lệ huỷ, không đến, số ca
theo bác sĩ), CN-49 số liệu cho trang Tổng quan. Ba endpoint `GET /reporting/summary`,
`/reporting/revenue`, `/reporting/appointments`, chỉ ADMIN.

**Không có database và không nghe sự kiện** — mỗi lần admin mở báo cáo, service hỏi thẳng
nơi đang giữ số liệu rồi ghép lại:

```
reporting --GET /orders/stats----> order     doanh thu đơn đã giao, theo completed_at
          --GET /payment/stats---> payment   tiền đơn thuốc đã thu, theo paid_at
          --GET /booking/stats---> booking   số lịch theo trạng thái, theo ngày khám và bác sĩ
          --GET /profile/doctors-> profile   họ tên bác sĩ
```

Chọn cách này thay vì read model nghe sự kiện vì các sự kiện hiện có không mang số tiền và
lịch hẹn không phát sự kiện khi huỷ hay khám xong; làm read model phải sửa ba service phát
sự kiện và dữ liệu cũ sẽ không có trong báo cáo. Đổi lại, báo cáo phụ thuộc service nguồn
lúc chạy — nên:

- Mỗi service nguồn tự gộp bằng SQL (`GROUP BY` ngày giờ Việt Nam), chỉ trả vài chục dòng.
- Token admin được chuyển nguyên sang; service nguồn kiểm tra lại quyền ADMIN. Nhân viên
  không đọc được doanh thu dù gọi thẳng service nguồn.
- Một nguồn không trả lời thì phần đó là `null` và nằm trong `unavailable`, phần còn lại vẫn
  hiện. `0` chỉ dùng cho "đã hỏi, thật sự không có". Timeout 2 s kết nối / 5 s đọc.

---

## 4. Luồng sự kiện RabbitMQ

```
auth     --user.registered------> notification    gửi mail xác minh tài khoản
auth     --user.deleted---------> profile         dọn hồ sơ mồ côi
order    --order.completed------> product         trừ kho
order    --order.cancelled------> product         hoàn kho
booking  --prescription.created-> payment         tạo phiếu thu tiền thuốc
payment  --payment.completed----> booking         mở đơn thuốc đã trả tiền
booking  --appointment.created--> notification   gửi email xác nhận lịch khám (CN-43)
auth     --user.staff-created----> profile        tạo hồ sơ bác sĩ kèm họ tên (VD-20)
auth     --user.locked/unlocked--> booking        chặn / mở lại giờ khám của bác sĩ bị khoá (CN-08)
```

Cả hai chiều trừ / hoàn kho đều **chống xử lý trùng message**: `product-service` kiểm tra
`stock_movements` theo `(productId, orderId, type)` trước khi áp dụng, nên một đơn có thể
vừa có dòng `SALE` vừa có dòng `RETURN` mà không đếm nhầm.

`appointment.created` mang sẵn email khách, tên thú cưng và tên bác sĩ, nên `notification-service` chỉ việc soạn email — không gọi ngược sang auth hay profile. Tra tên bác sĩ hỏng thì email bớt một dòng, việc đặt lịch không bị ảnh hưởng. Mọi giá trị khách tự gõ đều được escape HTML trước khi ghép vào email.

---

## 5. Bảng cổng thống nhất

Sau khi gộp hai nhánh phát triển song song, cổng được phân lại một lần cho toàn hệ thống:

| Service | Cổng | Database | Cổng host |
|---|---|---|---|
| api-gateway | 8080 | — | — |
| auth-service | 8081 | auth-db | 5433 |
| profile-service | 8083 | profile-db | 5434 |
| product-service | 8084 | product-db | 5435 |
| order-service | 8085 | order-db | 5436 |
| booking-service | 8086 | booking-db | 5437 |
| payment-service | 8087 | payment-db | 5438 |
| notification-service | 8088 | — | — |
| reporting-service | 8089 | — | — |
| eureka-server | 8761 | — | — |
| frontend | 3000 | — | — |

Mỗi service khai báo cổng ở **ba nơi** và phải khớp nhau: `docker-compose.yml`,
`application.yml` (`SERVER_PORT`) và `Dockerfile` (`EXPOSE`). Chỉ sửa compose là chưa đủ —
chạy local ngoài Docker vẫn sẽ đụng cổng.

---

## 6. Hạ tầng dùng chung

- **Service discovery** — Eureka, mọi service tự đăng ký; gateway định tuyến bằng
  `lb://` nên không cần biết địa chỉ cụ thể.
- **API Gateway** — 6 route khai báo tay, tắt auto-route theo tên service để client gọi
  thẳng `/auth/**`, `/products/**` như gọi trực tiếp.
- **Quản lý schema** — Flyway ở cả 6 service có database, `ddl-auto: validate` để
  Hibernate chỉ đối chiếu chứ không tự sinh hay sửa bảng.
- **Bảo mật** — JWT ký HS256, secret dùng chung qua biến môi trường; mỗi service tự
  verify, không gọi chéo.
- **Triển khai** — một lệnh `docker compose up` dựng toàn bộ. Hai service chưa code nằm
  trong profile `future` nên không bị kéo theo.

---

## 7. Còn thiếu

| Mã | Chức năng | Vì sao chưa có |
|---|---|---|
| CN-34 | Thanh toán trực tuyến | Mới có COD; cần tài khoản merchant và URL công khai nhận IPN |
| CN-38 → 41 | Chấm công, lịch làm việc, giờ công | `staff-service` chưa tồn tại |
| CN-44, 45 | Thông báo đơn hàng / nhắc tái khám | `notification-service` mới làm email xác minh và xác nhận đặt lịch |
| CN-48 | Báo cáo chấm công | Cần `staff-service`; CN-46, 47, 49 đã làm ngày 13/09 |

CN-07 (làm mới phiên) và CN-08 (khoá tài khoản) từng là code khai báo sẵn nhưng không hoạt động;
cả hai đã sửa ngày 13/09 — VD-05, VD-06.

---

## 8. Vấn đề đã biết

Chi tiết ở [van-de-ton-dong.md](van-de-ton-dong.md).

Ngày 13/09 đã sửa ba mục nặng nhất:

- **VD-01** — khách vãng lai liệt kê được hàng đã ẩn qua `?activeOnly=false`.
- **VD-05** — refresh token phát ra nhưng không dùng được, và không thu hồi được.
- `profile-db`, `booking-db`, `payment-db` **thiếu `volumes`**, mất sạch dữ liệu mỗi lần
  `docker compose down`. Giờ cả 6 database đều có volume và healthcheck; service tương ứng
  chờ database `healthy` mới khởi động. Đã kiểm chứng bằng việc xoá hẳn container, và một lần
  Docker sập thật — dữ liệu còn nguyên.

Cùng ngày sửa luôn **VD-16** — lịch hẹn giờ cho biết bác sĩ nào khám. Danh sách không còn mục 🔴 nào.

---

## 9. Ghi chú về quá trình

Dự án từng có hai nhánh phát triển song song tách nhau từ 28/08. Nhánh `master` làm
`product-service` và `order-service`, nhánh `huy` làm `booking`, `payment` và
`notification`. Cả hai cùng đánh số cổng từ 8084 mà không biết nhau, nên **mọi cổng mới
của nhánh này đều đụng nhánh kia**.

Hai nhánh đã được gộp ngày 09/09, cổng phân lại một lần cho cả 7 service. Bài học rút ra:
khi tách nhánh dài ngày, dải cổng và các file hạ tầng dùng chung (`docker-compose.yml`,
route gateway, `.env.example`) nên được chốt trước ở một chỗ, vì đó chính là nơi conflict
dồn về.
