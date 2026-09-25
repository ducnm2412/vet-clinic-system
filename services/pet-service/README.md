# pet-service

Hồ sơ thú cưng, bệnh án và đơn thuốc: con vật nào của khách nào, và mỗi lần đến khám thì bác sĩ
chẩn đoán gì, kê thuốc gì.

Trước đây phần này nằm trong `profile-service`; tách ra thành service riêng ngày 25/09/2026 (VD-10)
vì đây là thực thể trung tâm của phòng khám thú y — lịch hẹn, bệnh án và đơn thuốc đều trỏ vào nó,
nên để nó nằm lẫn trong hồ sơ hành chính của khách là sai chỗ.

- Port: `8091`
- Database riêng: `pet_db` (PostgreSQL, host `5440` khi chạy docker-compose)
- Không có bảng nào tham chiếu sang service khác: chủ nuôi lưu bằng `owner_user_id` (userId bên
  `auth-service`), không lưu `customer_profile_id` — `pet-service` không cần biết `profile-service`
  lưu hồ sơ khách thế nào.

## Endpoint

Hai nhánh tách hẳn nhau — khách và người trong phòng khám không dùng chung đường nào:

| Method | Đường dẫn | Quyền | Việc |
|---|---|---|---|
| `GET` | `/pets/me` | `CUSTOMER` | Thú cưng của chính mình |
| `GET` | `/pets/me/{petId}` | `CUSTOMER` | Một con của chính mình |
| `POST` | `/pets/me` | `CUSTOMER` | Thêm |
| `PUT` | `/pets/me/{petId}` | `CUSTOMER` | Sửa |
| `DELETE` | `/pets/me/{petId}` | `CUSTOMER` | Xoá |
| `GET` | `/pets` | `DOCTOR`, `STAFF`, `ADMIN` | Tra cứu, `?ids=` để lọc |
| `GET` | `/pets/{petId}` | `DOCTOR`, `STAFF`, `ADMIN` | Tra cứu một con |
| `GET` | `/pets/by-owners?ownerUserIds=` | `DOCTOR`, `STAFF`, `ADMIN` | Thú cưng của nhiều chủ một lượt (bảng Khách hàng của admin) |
| `GET` | `/pets/me/{petId}/medical-records` | `CUSTOMER` | Lịch sử khám của bé mình nuôi (CN-24) |
| `GET` | `/pets/{petId}/medical-records` | `DOCTOR`, `STAFF`, `ADMIN` | Lịch sử khám của một con vật (CN-24) |

### Bệnh án và đơn thuốc

| Method | Đường dẫn | Quyền | Việc |
|---|---|---|---|
| `PUT` | `/medical-records/by-appointment/{appointmentId}` | `DOCTOR` | Lập hoặc sửa bệnh án của một lượt khám (CN-23, CN-25) |
| `GET` | `/medical-records/by-appointment/{appointmentId}` | tất cả vai trò | Xem bệnh án; khách chỉ xem của mình |
| `PUT` | `/medical-records/by-appointment/{appointmentId}/receive` | `STAFF`, `ADMIN` | Giao thuốc sau khi đã thu tiền; còn `PENDING` thì 409 |
| `GET` | `/medical-records/pending` | `STAFF`, `ADMIN` | Hàng đợi đơn đã thu tiền, chờ giao |
| `GET` | `/medical-records/mine/outstanding` | `DOCTOR` | Bệnh án còn treo của chính bác sĩ đang đăng nhập |

`petId`, chủ nuôi và bác sĩ **không nhận từ client**: lúc lập bệnh án, `pet-service` hỏi
`booking-service` một lần (`GET /booking/appointments/{id}`, kèm token của bác sĩ) rồi chép ba
trường đó vào bệnh án. Tin body gửi lên thì một bác sĩ có thể gắn bệnh án vào con vật của người
khác. Mọi lần **đọc** về sau không gọi `booking-service` nữa — bệnh án là hồ sơ lâm sàng, phải tra
được cả khi service kia đang tắt, và ai khám thì vĩnh viễn là người đó.

Vòng đời đơn thuốc: `PENDING` (bác sĩ vừa kê) → `RECEIVED` khi `payment-service` báo đã thu tiền.
Phòng khám thu tiền và giao thuốc cùng một lượt ở quầy nên không có bước chờ tiếp nhận riêng;
`PAID` vẫn còn trong enum cho trường hợp thu trước giao sau.

Thú cưng đã có bệnh án thì **không xoá được hồ sơ** (409), và xoá tài khoản khách cũng không kéo
theo con đó: bệnh án là hồ sơ lâm sàng của phòng khám, không phải dữ liệu riêng của khách.

Chủ nuôi **luôn** lấy từ claim `userId` trong JWT, không bao giờ nhận từ body hay query — client
không có đường nào tự khai mình là chủ của con khác.

Khách hỏi con vật không phải của mình thì nhận **404**, không phải 403: 403 tự xác nhận con vật đó
có thật.

## Sự kiện

| Exchange | Routing key | Hướng | Xử lý |
|---|---|---|---|
| `user.events` | `user.deleted` | nhận | Xoá hồ sơ thú cưng của tài khoản vừa bị xoá, trừ con đã có bệnh án |
| `pet.events` | `prescription.created` | phát | `payment-service` nghe để tạo phiếu thu tiền thuốc |
| `payment.events` | `payment.completed` | nhận | Đóng đơn thuốc đã trả tiền (trước 25/09/2026 là `booking-service` nghe) |

## Gọi qua lại với service khác

- `booking-service` → `pet-service`: kiểm con vật có phải của khách đang đặt lịch không, và lấy
  tên/loài để hiển thị lịch hẹn.
- `pet-service` → `booking-service`: xác nhận lịch hẹn lúc bác sĩ lập bệnh án.

Hai chiều gọi nhau chỉ xảy ra lúc chạy, Feign khởi tạo lười nên không service nào phải chờ service
kia lúc khởi động. Cả hai chiều đều chuyển nguyên token của người dùng, không tự ký token nội bộ
(VD-24).
- Frontend — trang "Thú cưng của tôi" của khách và trang tra cứu của bác sĩ/nhân viên.

## Di trú dữ liệu

Hai chặng, mỗi chặng một script, cả hai chạy lại nhiều lần được và đều **giữ nguyên `id`**:

| Script | Từ | Ghi chú |
|---|---|---|
| `scripts/migrate-pets-to-pet-service.sh` | `profile_db.pets` | Join `customer_profiles` để đổi `customer_profile_id` thành `owner_user_id`; `booking_db` đang trỏ tới `id` của từng con |
| `scripts/migrate-medical-records-to-pet-service.sh` | `booking_db.medical_records` + `prescription_items` | Join `appointments` và `appointment_slots` để lấy `pet_id`, chủ nuôi và bác sĩ; `payment_db.payments.medical_record_id` đang trỏ tới `id` của bệnh án |

Bảng cũ ở cả hai database chỉ được đổi tên thành `*_moved_to_pet_service_backup`, không xoá.
