# Hướng dẫn bật thanh toán online bằng VNPAY (sandbox)

Áp dụng cho đơn hàng đặt qua giỏ hàng (CN-34), do `order-service` xử lý. Mặc định hệ thống chạy với **cổng
giả lập** để demo không cần tài khoản gì. Làm theo tài liệu này để chuyển sang VNPAY sandbox.

## 1. Luồng thanh toán

```
Khách bấm "Đặt hàng và thanh toán"
  └─> order-service tạo đơn (PENDING, chưa trả, hạn 30 phút) và cấp link VNPAY
        └─> Khách trả trên trang VNPAY
              ├─> (A) IPN: VNPAY gọi máy chủ  GET /orders/pay/vnpay/ipn      ← nguồn chính thức
              └─> (B) Trình duyệt khách bị đưa về  <web>/pay/vnpay-return
                        └─> trang web gọi  GET /orders/pay/vnpay/return
```

- Cả (A) và (B) đều kiểm chữ ký rồi đi qua cùng một hàm ghi nhận, nên gọi cái nào trước hay gọi lặp đều
  an toàn. Trả xong đơn vẫn `PENDING`, nhân viên xác nhận như đơn COD.
- Nếu IPN không tới được máy chủ (ví dụ chạy trên máy cá nhân không có địa chỉ công khai) thì (B) vẫn ghi
  nhận được **khi khách quay về trang web**. Khách đóng trình duyệt giữa chừng thì đơn vẫn hiện chưa trả cho
  tới khi job quét đơn quá hạn hỏi lại VNPAY (xem mục 6).
- Đơn không trả sau hạn thì job tự huỷ, nhưng **hỏi VNPAY trước** để không huỷ nhầm đơn đã trả mà thông báo
  bị lỡ.

## 2. Việc cần chuẩn bị

1. **Tài khoản sandbox VNPAY.** Đăng ký ở <http://sandbox.vnpayment.vn/devreg/>. VNPAY gửi email gồm
   `TmnCode` (mã website), `HashSecret` (khoá ký) và tài khoản vào cổng quản trị sandbox.
2. **Một địa chỉ công khai cho IPN** — chỉ cần nếu muốn nhận IPN (mục 4). `localhost` thì VNPAY không gọi tới
   được.

## 3. Cấu hình `.env`

```env
# Chỉ được bật MỘT cổng: tắt cổng giả lập khi bật VNPAY, nếu không order-service từ chối khởi động.
ORDER_MOCK_GATEWAY_ENABLED=false

ORDER_VNPAY_ENABLED=true
ORDER_VNPAY_TMN_CODE=<TmnCode trong email>
ORDER_VNPAY_HASH_SECRET=<HashSecret trong email>

# Trang web khách được đưa về sau khi trả tiền. Mở bằng trình duyệt của chính khách nên localhost dùng được.
ORDER_VNPAY_RETURN_URL=http://127.0.0.1:3000/pay/vnpay-return

# Đã có mặc định cho sandbox, chỉ đổi khi lên môi trường thật theo thông tin VNPAY cấp.
ORDER_VNPAY_PAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
ORDER_VNPAY_QUERY_URL=https://sandbox.vnpayment.vn/merchant_webapi/api/transaction
ORDER_VNPAY_SERVER_IP=127.0.0.1
```

Áp dụng cấu hình:

```bash
docker compose up -d --force-recreate --no-deps order-service
```

Kiểm tra service lên bình thường và không báo thiếu cấu hình:

```bash
docker logs order-service 2>&1 | grep -E "Started OrderServiceApplication|APPLICATION FAILED"
```

`ORDER_VNPAY_RETURN_URL` phải là địa chỉ mà **trình duyệt của khách** mở được, cùng địa chỉ khách đang dùng để
vào web (`127.0.0.1` hay `localhost` đều được nhưng đừng trộn lẫn, vì phiên đăng nhập gắn với từng tên miền).

## 4. Nhận IPN: dựng đường hầm

VNPAY gọi IPN từ internet, nên máy chủ phải có địa chỉ công khai. Khi phát triển trên máy cá nhân, dùng một
đường hầm trỏ vào **API Gateway (cổng 8080)** — gateway đã chuyển `/orders/**` sang order-service.

Chọn một trong hai:

```bash
# cloudflared (không cần tài khoản cho đường hầm tạm)
cloudflared tunnel --url http://127.0.0.1:8080

# hoặc ngrok
ngrok http 8080
```

Lệnh in ra một địa chỉ dạng `https://xxxx.trycloudflare.com` (hoặc `https://xxxx.ngrok-free.app`). Khai trong
cổng quản trị sandbox của VNPAY (mục cấu hình IPN URL của website, tên menu có thể thay đổi theo VNPAY):

```
https://xxxx.trycloudflare.com/orders/pay/vnpay/ipn
```

Lưu ý:

- Đường hầm miễn phí **đổi địa chỉ mỗi lần chạy lại**, mỗi lần phải sửa lại IPN URL trong cổng quản trị.
- Kiểm tra đường hầm đã thông (chưa bật VNPAY sẽ thấy `RspCode 99`, bật rồi và thiếu chữ ký thì thấy `97`):

  ```bash
  curl "https://xxxx.trycloudflare.com/orders/pay/vnpay/ipn"
  ```
- Không dựng được đường hầm vẫn thử được: lúc đó chỉ có (B), xem mục 1.

## 5. Chạy thử trên sandbox

Dùng thẻ test do VNPAY công bố (trang demo sandbox, có thêm các thẻ cho tình huống số dư không đủ, thẻ khoá...):

| Trường | Giá trị |
|---|---|
| Ngân hàng | NCB |
| Số thẻ | `9704198526191432198` |
| Tên chủ thẻ | `NGUYEN VAN A` |
| Ngày phát hành | `07/15` |
| Mật khẩu OTP | `123456` |

Các tình huống nên thử, bằng tài khoản khách và tài khoản nhân viên:

| # | Làm gì | Kết quả đúng |
|---|---|---|
| 1 | Đặt hàng, chọn "Thanh toán online", trả bằng thẻ test | Về trang "Thanh toán thành công"; đơn `Đã thanh toán, chờ xác nhận`; nhân viên xác nhận được rồi giao |
| 2 | Vào trang VNPAY rồi bấm huỷ | Trang "Chưa thanh toán", đơn vẫn chờ trả; bấm "Thanh toán ngay" ở trang đơn thanh toán lại được |
| 3 | Đặt hàng rồi không trả, chờ quá hạn (đặt `ORDER_ONLINE_PAYMENT_TIMEOUT_MINUTES=3` để thử cho nhanh) | Sau khoảng hạn + 2 phút, đơn tự huỷ với lý do "Quá hạn thanh toán online" |
| 4 | Trả xong, đóng trình duyệt trước khi về web, tắt đường hầm | Đơn hiện chưa trả; sau hạn + 2 phút job hỏi VNPAY và ghi nhận đã trả thay vì huỷ |
| 5 | Nhân viên mở đơn online chưa trả | Không có nút Xác nhận |
| 6 | Khách huỷ đơn đã trả | Đơn `Đã huỷ, chờ hoàn tiền`; nhân viên thấy thẻ "Cần hoàn tiền" |

Theo dõi log khi thử:

```bash
docker logs -f order-service 2>&1 | grep -iE "vnpay|thanh toán|quá hạn"
```

## 6. Xử lý sự cố

| Dấu hiệu | Nguyên nhân thường gặp |
|---|---|
| Trang VNPAY báo "Sai chữ ký" | `TmnCode` hoặc `HashSecret` sai (có khoảng trắng thừa khi dán), hoặc dùng cặp của môi trường khác |
| Bấm đặt hàng báo "Thanh toán online hiện chưa khả dụng" | `ORDER_VNPAY_ENABLED` chưa là `true` (và cổng giả lập cũng tắt), hoặc order-service chưa được tạo lại sau khi sửa `.env` |
| order-service không khởi động, log nói "Chỉ được bật một cổng" | Cả `ORDER_MOCK_GATEWAY_ENABLED` và `ORDER_VNPAY_ENABLED` đều `true` |
| order-service không khởi động, log nói thiếu `order.vnpay.*` | Bật VNPAY mà chưa điền `TMN_CODE`, `HASH_SECRET` hoặc `RETURN_URL` |
| Trả xong mà đơn vẫn chưa trả | IPN không tới (đường hầm chết, IPN URL cũ) **và** khách chưa về trang web. Mở trang đơn sau vài phút, hoặc chờ job quét |
| IPN báo `RspCode 97` trong log VNPAY | Sai `HashSecret` ở phía mình |
| IPN báo `RspCode 01` | VNPAY gọi với mã giao dịch không có trong đơn nào, thường do khách dùng link cũ sau khi lấy link mới |
| IPN báo `RspCode 04` | Số tiền VNPAY ghi khác tổng đơn; đơn không được tính là đã trả, cần kiểm tra giao dịch |

Mỗi lần khách bấm "Thanh toán ngay" hệ thống cấp **mã giao dịch mới và vô hiệu mã cũ**, nên kết quả muộn của
link cũ bị coi là giao dịch lạ.

## 7. Lên môi trường thật

- VNPAY cấp `TmnCode`, `HashSecret` và địa chỉ thật sau khi ký hợp đồng; thay `PAY_URL` và `QUERY_URL` theo
  tài liệu họ cung cấp.
- Đặt `ORDER_VNPAY_RETURN_URL` về tên miền thật và khai IPN URL về máy chủ thật (không dùng đường hầm).
- **Tắt hẳn cổng giả lập** (`ORDER_MOCK_GATEWAY_ENABLED=false`): cổng đó cho "trả tiền" mà không có tiền thật.
- Hoàn tiền hiện làm **thủ công** qua cổng quản trị VNPAY, hệ thống chỉ đánh dấu đơn "cần hoàn tiền".

## 8. Những gì chưa kiểm được ngoài sandbox

Mã ký và kiểm chữ ký đã được test với một cài đặt độc lập theo đúng mô tả trong tài liệu VNPAY, nhưng chưa
được đối chiếu với giao dịch thật của VNPAY. Hai điểm cần xác nhận ở lần chạy sandbox đầu tiên:

1. Giao dịch đi hết luồng, không bị VNPAY báo "Sai chữ ký".
2. API hỏi lại giao dịch (`querydr`): số tiền trong phản hồi được hiểu là đã nhân 100 như IPN. Nếu thực tế
   khác, tình huống 4 ở mục 5 sẽ không ghi nhận đơn đã trả mà đưa vào nhánh hoàn tiền thủ công; khi đó log của
   order-service ghi rõ số tiền lệch.
