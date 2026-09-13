# Reporting Service

Báo cáo cho quản trị viên: CN-46 doanh thu, CN-47 thống kê lịch khám, CN-49 số liệu trang
Tổng quan. Cổng `8089`, gateway chuyển `/reporting/**` vào đây. CN-48 (chấm công) chờ
`staff-service`.

## Endpoint — chỉ ADMIN

| Endpoint | Tham số | Trả về |
|---|---|---|
| `GET /reporting/summary` | `from`, `to` | Tổng doanh thu, số đơn tạo/giao/huỷ, số lịch theo trạng thái, tỉ lệ huỷ |
| `GET /reporting/revenue` | `from`, `to`, `interval=day\|month\|quarter` | Doanh thu từng kỳ, tách bán hàng và tiền thu tại quầy |
| `GET /reporting/appointments` | `from`, `to`, `interval` | Số lịch từng kỳ, tỉ lệ huỷ / không đến, bảng theo bác sĩ |

`from`/`to` dạng `yyyy-MM-dd`, giờ Việt Nam, tính cả hai đầu. Bỏ trống thì là 30 ngày gần
nhất. Tối đa 731 ngày; xem theo ngày tối đa 92 ngày. Sai thì 400 kèm lý do.

## Cách lấy số liệu

Service **không có database và không nghe sự kiện**. Mỗi lần gọi, nó hỏi service đang giữ
số liệu (qua Eureka, bằng Feign) rồi ghép lại:

| Nguồn | Endpoint | Đếm theo |
|---|---|---|
| order-service | `GET /orders/stats` | Doanh thu đơn `COMPLETED` theo `completed_at` (COD: giao xong mới thu tiền) |
| payment-service | `GET /payment/stats` | Phiếu thu `COMPLETED` theo `paid_at` |
| booking-service | `GET /booking/stats` | Lịch hẹn theo ngày khám (ngày của slot), không theo ngày đặt |
| profile-service | `GET /profile/doctors` | Họ tên bác sĩ |

Ba endpoint nguồn cũng chỉ cho ADMIN. reporting-service chuyển nguyên token của admin sang,
nên không có đường nào để nhân viên đọc doanh thu.

## Khi một nguồn hỏng

- `summary` và `revenue`: phần của nguồn đó là `null`, tên nguồn nằm trong `unavailable`
  (`sales`, `services`, `appointments`). Phần còn lại vẫn trả 200.
- `appointments` chỉ có một nguồn, nên booking-service hỏng thì trả **503**.
- profile-service hỏng thì bảng bác sĩ thiếu tên, số liệu vẫn đúng.

`null` khác `0`: `0` là đã hỏi và thật sự không có. Giao diện hiện `—` cho `null`.
Timeout Feign: 2 s kết nối, 5 s đọc.

## Test

```bash
mvn test -DargLine="-Duser.timezone=Asia/Ho_Chi_Minh"
```

Không cần database, RabbitMQ hay Eureka — nguồn số liệu là mock.
