# order-service

Giỏ hàng, đặt hàng và xử lý đơn cho phần bán hàng trực tuyến.

- **Database:** `order_db` (PostgreSQL, cổng host `5436`)
- **Cổng service:** `8085`
- **Chức năng:** CN-32 → CN-37 trong `docs/phan-tich-chuc-nang.md`
- **Thanh toán:** COD. CN-34 (VNPay/Momo) chưa làm — cần tài khoản merchant và URL công
  khai để nhận IPN callback, `localhost` thì cổng thanh toán không gọi tới được.

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

### Xử lý đơn — CN-36 (role `STAFF` hoặc `ADMIN`)

| Method | Path |
|---|---|
| GET | `/orders/manage?status=PENDING` |
| GET | `/orders/manage/{id}` |
| POST | `/orders/manage/{id}/confirm` |
| POST | `/orders/manage/{id}/ship` |
| POST | `/orders/manage/{id}/complete` |
| POST | `/orders/manage/{id}/cancel` |

Đường dẫn quản trị đặt dưới `/orders/manage/**` chứ không phải `/admin/**` — gateway đã dành
`/admin/**` cho `auth-service`.

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

29 test: 15 service (checkout, luồng trạng thái, phát sự kiện, phân quyền xem đơn),
6 luật chuyển trạng thái, 8 validation DTO.
