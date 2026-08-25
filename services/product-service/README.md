# product-service

Quản lý danh mục, sản phẩm và tồn kho cho phần bán hàng trực tuyến.

- **Database:** `product_db` (PostgreSQL, cổng host `5435`)
- **Cổng service:** `8084`
- **Chức năng:** CN-27 → CN-31 trong `docs/phan-tich-chuc-nang.md`

## Endpoint

Gọi qua API Gateway (`http://localhost:8080`) hoặc trực tiếp `http://localhost:8084`.

### Danh mục — CN-27

| Method | Path | Quyền |
|---|---|---|
| GET | `/categories` | Công khai |
| GET | `/categories/{id}` | Công khai |
| POST | `/categories` | ADMIN |
| PUT | `/categories/{id}` | ADMIN |
| DELETE | `/categories/{id}` | ADMIN |

Không xoá được danh mục còn sản phẩm — trả `409` thay vì để lỗi khoá ngoại.

### Sản phẩm — CN-28, CN-29

| Method | Path | Quyền |
|---|---|---|
| GET | `/products` | Công khai |
| GET | `/products/{id}` | Công khai |
| POST | `/products` | ADMIN |
| PUT | `/products/{id}` | ADMIN |
| DELETE | `/products/{id}` | ADMIN |

Tham số lọc của `GET /products`: `categoryId`, `keyword` (tìm trong tên, không phân biệt
hoa thường), `minPrice`, `maxPrice`, `activeOnly` (mặc định `true`), cùng `page`, `size`,
`sort` chuẩn Spring Data. Mặc định 20 bản ghi mỗi trang, sắp theo tên.

### Tồn kho — CN-30

| Method | Path | Quyền |
|---|---|---|
| GET | `/products/low-stock` | STAFF, ADMIN |
| GET | `/products/{id}/stock-movements` | STAFF, ADMIN |
| POST | `/products/{id}/stock` | STAFF, ADMIN |

`POST /products/{id}/stock` nhận `{ "type": "IMPORT|ADJUSTMENT|RETURN", "quantityChange": 10, "note": "..." }`.
Chỉ `ADJUSTMENT` được nhận số âm (kiểm kê thiếu); `IMPORT` và `RETURN` phải dương. `SALE`
không gọi được qua API — nó chỉ sinh từ sự kiện RabbitMQ.

Trừ quá tồn hiện có trả `409`, không cho tồn kho âm.

## Quyết định thiết kế

**Không dùng prefix `/admin`.** Gateway đã định tuyến `/admin/**` sang `auth-service`, nên
service này phân quyền theo HTTP method trên cùng một prefix tài nguyên: `GET` công khai,
`POST/PUT/DELETE` cần ADMIN. Quy tắc khai tập trung ở `SecurityConfig`.

**Tồn kho có hai nơi ghi.** `products.stock_quantity` giữ tồn hiện tại để lọc và hiển thị
nhanh; `stock_movements` ghi vết từng lần biến động kèm `quantity_after` để đối soát mà
không phải cộng dồn cả lịch sử. Tồn khởi tạo lúc tạo sản phẩm cũng sinh một dòng `IMPORT`.

**Sửa tồn kho phải qua endpoint riêng.** `PUT /products/{id}` cố tình bỏ qua trường
`initialStock` — đổi tồn bắt buộc đi qua `POST /products/{id}/stock` để luôn có vết.

## Trừ kho tự động — CN-31

Service lắng nghe queue `product-service.order-completed`, bind vào exchange `order.events`
với routing key `order.completed`.

```json
{
  "orderId": "uuid-cua-don-hang",
  "lines": [ { "productId": "uuid-san-pham", "quantity": 2 } ]
}
```

`order-service` chưa được code — cấu trúc trên là hợp đồng thoả thuận trước, khi làm tới
nơi chỉ cần publish đúng dạng này.

Hai điểm đã xử lý sẵn:

- **Chống trùng.** RabbitMQ giao message theo kiểu at-least-once nên cùng một sự kiện có
  thể tới hai lần. Chỉ mục duy nhất trên `(product_id, reference_id, type)` cộng với kiểm
  tra trước khi ghi đảm bảo mỗi đơn chỉ trừ kho một lần.
- **Không nghẽn queue.** Sản phẩm đã bị xoá hoặc kho thực sự không đủ thì ghi log lỗi rồi
  bỏ qua dòng đó, thay vì ném lại khiến broker giao lại vô hạn mà kết quả vẫn thế.

## Chạy và kiểm thử

Service chạy cùng toàn hệ thống bằng `docker compose up -d --build` ở thư mục gốc.

Chạy test cần `product-db` đang bật (`docker compose up -d product-db`) và các biến
`DB_PORT=5435`, `DB_PASSWORD`, `JWT_SECRET`. Test controller tắt listener RabbitMQ nên
không cần broker.

```
mvn test
```

32 test: 14 controller (phân quyền, luồng HTTP), 8 service (logic tồn kho, chống trùng
sự kiện), 10 validation DTO.
