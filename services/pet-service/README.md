# pet-service

Hồ sơ thú cưng: con vật nào của khách nào, tên, loài, giống, giới tính, ngày sinh, cân nặng.

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

Chủ nuôi **luôn** lấy từ claim `userId` trong JWT, không bao giờ nhận từ body hay query — client
không có đường nào tự khai mình là chủ của con khác.

Khách hỏi con vật không phải của mình thì nhận **404**, không phải 403: 403 tự xác nhận con vật đó
có thật.

## Sự kiện

| Exchange | Routing key | Hướng | Xử lý |
|---|---|---|---|
| `user.events` | `user.deleted` | nhận | Xoá toàn bộ hồ sơ thú cưng của tài khoản vừa bị xoá (trước đây khoá ngoại của `profile_db` lo việc này) |

## Ai gọi service này

- `booking-service` — kiểm con vật có phải của khách đang đặt lịch không, và lấy tên/loài để hiển
  thị lịch hẹn. Gọi kèm token của người dùng, nên `pet-service` tự quyết quyền, `booking-service`
  không phải tự đoán.
- Frontend — trang "Thú cưng của tôi" của khách và trang tra cứu của bác sĩ/nhân viên.

## Di trú dữ liệu

Dữ liệu cũ trong `profile_db.pets` chuyển sang bằng `scripts/migrate-pets-to-pet-service.sh`
(giữ nguyên `id` của từng con vì `booking_db` đang trỏ tới). Bảng cũ được đổi tên thành
`pets_moved_to_pet_service_backup` chứ không xoá.
