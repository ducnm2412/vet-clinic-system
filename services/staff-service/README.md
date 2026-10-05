# Staff Service

Chấm công và ca trực: CN-38 (vào/ra ca), CN-39 (xếp ca theo tuần), CN-40 (tổng giờ công),
CN-41 (phát sự kiện ca trực), CN-48 (báo cáo chấm công). Cổng `8090`, database `staff_db`,
gateway chuyển `/staff/**` vào đây.

## Endpoint

| Endpoint | Ai gọi được | Việc |
|---|---|---|
| `POST /staff/shifts` | ADMIN | Xếp ca, nhiều ngày một lượt (tối đa 31) |
| `GET /staff/shifts?from=&to=&userId=` | STAFF, DOCTOR, ADMIN | Lịch làm việc trong khoảng |
| `DELETE /staff/shifts/{id}` | ADMIN | Bỏ một ca |
| `POST /staff/attendance/check-in` \| `/check-out` | STAFF, DOCTOR, ADMIN | Chấm công cho **chính mình** |
| `GET /staff/attendance/me/today`, `/me?from=&to=` | STAFF, DOCTOR, ADMIN | Ngày công của mình |
| `GET /staff/attendance?from=&to=&userId=` | ADMIN | Chấm công cả phòng khám |
| `GET /staff/attendance/timesheet?from=&to=` | ADMIN | Bảng công theo người (CN-48) |

Người chấm công lấy từ token, không nhận `userId` từ client — nếu không thì ai cũng chấm công
hộ người khác.

## Ca trực quyết định giờ khám (VD-11)

Xếp hoặc bỏ ca thì service phát `shift.added` / `shift.removed` lên exchange `staff.events`.
`booking-service` nghe và:

- **thêm ca** → mở khung giờ khám trong phạm vi ca đó (chỉ trong giờ làm việc của phòng khám);
- **bỏ ca** → đóng những khung giờ còn trống; lịch khách đã đặt giữ nguyên, việc huỷ là quyết
  định của con người.

Vì vậy **bác sĩ không có ca thì ngày đó khách không đặt được lịch**. Trước ngày 24/09/2026 mọi
bác sĩ đều có đủ khung giờ mọi ngày, kể cả ngày nghỉ.

## Ngày giờ

Mọi mốc "hôm nay" tính theo giờ Việt Nam, không theo giờ máy chủ (container chạy UTC). Bảng
`attendance_records` lưu `date` riêng thay vì suy từ `check_in_at` — suy ngược sẽ lệch với ca tối.

Một người một ngày một bản ghi chấm công: vào ca hai lần hoặc ra ca khi chưa vào đều bị từ chối
(409) kèm câu giải thích hiện thẳng lên màn hình.

## Test

```bash
bash scripts/test.sh staff-service
```

Cần `staff-db` đang chạy; test dùng `staff_db_test` (VD-12).
