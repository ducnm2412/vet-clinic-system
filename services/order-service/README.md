# order-service

Giỏ hàng, đặt hàng và xử lý đơn cho phần bán hàng trực tuyến.

- **Database:** `order_db` (PostgreSQL, cổng host `5436`)
- **Cổng service:** `8085`
- **Chức năng:** CN-32 → CN-37 trong `docs/phan-tich-chuc-nang.md`
- **Thanh toán:** COD, thanh toán online qua cổng (VNPAY sandbox, hoặc cổng giả lập để demo) và thu
  tiền mặt/chuyển khoản tại quầy cho hoá đơn gộp. Xem [Thanh toán online](#thanh-toán-online--cn-34) và
  [hướng dẫn bật VNPAY](../../docs/huong-dan-thanh-toan-vnpay.md).

## Luồng trạng thái đơn — CN-35

```
PENDING ──confirm──> CONFIRMED ──ship──> SHIPPING ──complete──> COMPLETED
   │                     │
   └──cancel──> CANCELLED <──cancel──┘
```

| Trạng thái | Ý nghĩa | Kho |
|---|---|---|
| `PENDING` | Khách vừa đặt, chờ nhân viên xác nhận | chưa trừ |
| `CONFIRMED` | Nhân viên đã xác nhận, chốt bán | **đã trừ** |
| `SHIPPING` | Đang giao | đã trừ |
| `COMPLETED` | Đã giao và thu tiền | đã trừ |
| `CANCELLED` | Đã huỷ | hoàn lại nếu từng trừ |

Luật chuyển tiếp khai ngay trong enum `OrderStatus.allowedNext()` để nằm một chỗ, không rải
rác trong service. `SHIPPING` không huỷ được — hàng đang trên đường thì phải giao xong rồi
làm đơn trả.

## Endpoint

Gọi qua API Gateway (`http://localhost:8080`) hoặc trực tiếp `http://localhost:8085`.

### Giỏ hàng — CN-32 (role `CUSTOMER`)

| Method | Path |
|---|---|
| GET | `/cart` |
| POST | `/cart/items` |
| PUT | `/cart/items/{productId}` |
| DELETE | `/cart/items/{productId}` |
| DELETE | `/cart` |

Giỏ chỉ lưu `productId` + `quantity`. Giá và tình trạng còn hàng lấy trực tiếp từ
`product-service` mỗi lần đọc, nên luôn là giá mới nhất — giống cách các sàn thương mại
điện tử hoạt động. Response có cờ `available` cho từng dòng và `checkoutable` cho cả giỏ để
giao diện cảnh báo trước khi khách bấm đặt.

Thêm lại sản phẩm đã có trong giỏ thì cộng dồn số lượng, không tạo dòng thứ hai.

### Đơn hàng của khách — CN-33, CN-35

| Method | Path | Ghi chú |
|---|---|---|
| POST | `/orders` | Checkout từ giỏ |
| GET | `/orders` | Danh sách đơn của mình, có phân trang |
| GET | `/orders/{id}` | Chi tiết |
| GET | `/orders/{id}/history` | Vết chuyển trạng thái |
| POST | `/orders/{id}/cancel` | Chỉ huỷ được khi còn `PENDING` |

| POST | `/orders/{id}/pay` | Lấy link trả tiền đơn online (cấp mã giao dịch mới, vô hiệu mã cũ) |

### Xử lý đơn — CN-36 (role `STAFF` hoặc `ADMIN`)

| Method | Path | Ghi chú |
|---|---|---|
| GET | `/orders/manage?status=PENDING` | |
| GET | `/orders/manage/{id}` | |
| POST | `/orders/manage/{id}/confirm` | Đơn online chưa trả bị từ chối |
| POST | `/orders/manage/{id}/ship` | |
| POST | `/orders/manage/{id}/complete` | |
| POST | `/orders/manage/{id}/cancel` | |
| POST | `/orders/manage/counter` | Lập và thu hoá đơn gộp tại quầy (khám + sản phẩm) |

Đường dẫn quản trị đặt dưới `/orders/manage/**` chứ không phải `/admin/**` — gateway đã dành
`/admin/**` cho `auth-service`.

## Thanh toán online — CN-34

Khách chọn COD hoặc online khi đặt hàng. Đơn online tạo ra ở `PENDING` + `UNPAID` với hạn trả
(`ORDER_ONLINE_PAYMENT_TIMEOUT_MINUTES`, mặc định 30 phút) và **không** thêm trạng thái mới: trả xong đơn
vẫn `PENDING`, nhân viên xác nhận như thường. Kho chỉ trừ lúc xác nhận.

| Quy tắc | Chi tiết |
|---|---|
| Xác nhận | Đơn online chưa trả không xác nhận được |
| Hết hạn | Job quét mỗi phút, đơn quá hạn 2 phút mà chưa trả thì tự huỷ — sau khi **hỏi lại cổng** để không huỷ nhầm đơn đã trả mà thông báo bị lỡ |
| Huỷ sau khi trả | Đơn `CANCELLED` + `PAID` = cần hoàn tiền thủ công (cờ `refundRequired`) |
| Trả muộn sau khi đơn bị huỷ | Vẫn ghi nhận đã trả, rơi vào trạng thái cần hoàn tiền |

Cổng nằm sau interface `OnlinePaymentGateway`; chỉ **một** cổng được bật (`OnlineGatewayExclusivityCheck`):

| Cổng | Bật bằng | Dùng khi |
|---|---|---|
| `VnpayGateway` | `ORDER_VNPAY_ENABLED=true` + `TMN_CODE`, `HASH_SECRET`, `RETURN_URL` | Sandbox/thật |
| `MockPaymentGateway` | `ORDER_MOCK_GATEWAY_ENABLED=true` + `SECRET` | Demo, test offline. **Không bật ở môi trường thật** |

Endpoint cổng gọi về (không cần đăng nhập, tin vào chữ ký):

| Method | Path | Ghi chú |
|---|---|---|
| GET | `/orders/pay/vnpay/ipn` | IPN của VNPAY, phản hồi `{"RspCode","Message"}` theo định dạng của họ |
| GET | `/orders/pay/vnpay/return` | Trang web chuyển tham số khách được đưa về; cũng ghi nhận, không trùng lặp với IPN |
| POST | `/orders/pay/callback` | Kết quả chung dạng JSON (cổng khác) |
| POST | `/orders/pay/mock/submit` | Chỉ cổng giả lập, cần đăng nhập |

Mọi kết quả đi qua `OnlinePaymentService.recordGatewayResult` — khoá dòng, không trùng lặp, kiểm số tiền.
Chi tiết cấu hình, đường hầm cho IPN và kịch bản thử: [`docs/huong-dan-thanh-toan-vnpay.md`](../../docs/huong-dan-thanh-toan-vnpay.md).

## Quyết định thiết kế

**Giá và tồn kho luôn hỏi `product-service`, không tin client.** Nếu tin giá client gửi lên
thì khách sửa payload là mua được giá tuỳ ý. Gọi qua OpenFeign với tên `product-service`
(tra Eureka, không hardcode host/port).

**Đơn chụp lại thông tin tại thời điểm đặt.** `order_items` giữ riêng `sku`, `product_name`,
`unit_price`; `orders` giữ riêng tên/điện thoại/địa chỉ người nhận. Shop đổi giá hay khách
sửa sổ địa chỉ về sau thì đơn cũ không đổi theo, và vẫn đọc được cả khi sản phẩm đã bị xoá.

**Không có khoá ngoại sang `product_db`.** Mỗi service một database, tham chiếu chéo là vi
phạm Database per Service. `cart_items.product_id` và `order_items.product_id` chỉ là UUID
trần.

**Mã đơn cho người đọc.** Dạng `VC260825-A1B2` để khách đọc qua điện thoại, sinh bằng
`SecureRandom` và bỏ các ký tự dễ nhầm (`0`/`O`, `1`/`I`).

**Xem đơn của người khác trả 404, không phải 403.** Trả 403 là gián tiếp xác nhận đơn đó có
thật, giúp người lạ dò được id hợp lệ.

## Sự kiện RabbitMQ — CN-37

Publish lên exchange `order.events`:

| Routing key | Khi nào | Bên nhận |
|---|---|---|
| `order.completed` | Nhân viên **xác nhận** đơn | `product-service` trừ tồn kho |
| `order.cancelled` | Huỷ đơn **đã từng trừ kho** | `product-service` hoàn hàng về kho |
| `order.invoice-paid` | Hoá đơn gộp tại quầy có khoản khám đã thu | `payment-service` hoàn tất khoản khám đó |

```json
{ "orderId": "...", "lines": [ { "productId": "...", "quantity": 2 } ] }
```

**Vì sao trừ kho lúc xác nhận chứ không phải lúc giao xong:** nếu đợi tới `COMPLETED` thì
hàng đã hứa cho đơn này vẫn nằm trong kho và bán tiếp được cho khách khác, dẫn tới bán quá số
lượng thực có.

**Vì sao publish sau khi commit:** `OrderEventPublisher` dùng
`@TransactionalEventListener(AFTER_COMMIT)`. Publish thẳng trong service thì transaction
rollback sau đó sẽ để lại một message "đơn đã xác nhận" đã bay đi, và `product-service` trừ
kho cho một đơn không tồn tại.

## Chạy và kiểm thử

Service chạy cùng toàn hệ thống bằng `docker compose up -d --build` ở thư mục gốc.

Chạy test cần `order-db` đang bật (`docker compose up -d order-db`) và các biến `DB_PORT=5436`,
`DB_PASSWORD`, `JWT_SECRET`.

```
mvn test
```

Gần 170 test. Phần lớn là unit test với repository giả; một số chạy trên PostgreSQL thật và cần
database `order_db_test` (`bash scripts/create-test-databases.sh`), gồm phân quyền qua toàn chuỗi filter,
câu SQL thống kê doanh thu, IPN/trang trả về của VNPAY và job tự huỷ đơn quá hạn. Test không bao giờ chạy
vào `order_db` thật (`TestDatabaseGuard`). Test cổng VNPAY dùng bản giả cho phần gọi mạng, chưa gọi sandbox
thật.
