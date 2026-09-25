# Vấn đề tồn đọng

Danh sách nợ kỹ thuật và chức năng còn thiếu, ghi lại để không quên khi quay lại.
Cập nhật lần cuối: 13/09/2026.

Mức độ: 🔴 cần sửa sớm · 🟡 nên sửa · 🟢 chờ phụ thuộc · ⚪ không gấp · ✅ đã sửa

---

## product-service

### ✅ VD-01. Khách vãng lai xem được hàng đã ẩn — đã sửa 13/09/2026

`GET /products` là endpoint công khai, nhưng tham số `activeOnly` do client tự truyền. Ai
cũng thêm `?activeOnly=false` là liệt kê được hàng đã ngừng bán.

**Đã sửa:** `ProductController.search` đọc quyền từ `Authentication`. Chỉ `STAFF`/`ADMIN` mới
tắt được bộ lọc; khách vãng lai và `CUSTOMER` luôn bị ép về `activeOnly=true` dù truyền gì.

Kiểm chứng trên hệ thống thật với một sản phẩm `active=false`:

```
Khách không token, ?activeOnly=false   -> 0   (trước khi sửa: 1)
CUSTOMER,          ?activeOnly=false   -> 0
STAFF,             ?activeOnly=false   -> 1
ADMIN,             ?activeOnly=false   -> 1
```

Có 4 test hồi quy trong `ProductControllerTest` — hai test kịch bản tấn công được chạy
**trước** khi sửa để chắc chúng đỏ thật.

Phần còn lại — `GET /products/{id}` trả hàng đã ẩn cho bất kỳ ai biết UUID — **đã đóng nốt ngày
24/09**, xem VD-24.

### ✅ VD-02. Không có khoá chống tranh chấp khi sửa tồn kho — đã sửa 24/09/2026

`adjustStock` và `applySale` đọc `stockQuantity` rồi ghi đè, không khoá gì. Hai nhân viên bấm
nhập kho cùng lúc là một lần nhập biến mất.

**Đã sửa:** `ProductRepository.findByIdForUpdate` khoá dòng sản phẩm (`SELECT ... FOR UPDATE`),
mọi chỗ ĐỔI tồn kho đều đi qua nó — người thứ hai đợi người thứ nhất commit rồi mới đọc. Cùng
cách booking-service khoá khung giờ khám.

Chọn khoá dòng thay vì `@Version`: nhân viên không phải gặp lỗi "có người vừa sửa, thử lại", và
không phải viết vòng thử lại. `ProductStockConcurrencyTest` chạy hai luồng thật trên database
test; bỏ khoá ra là hai test hỏng ngay (hai đơn cùng mua món cuối đều qua, và 10+5+7 ra 17).

### ✅ VD-03. Xoá sản phẩm làm mất sạch lịch sử kho — đã sửa 25/09/2026

`stock_movements.product_id` có `ON DELETE CASCADE`, nên `DELETE /products/{id}` cuốn theo
toàn bộ vết nhập/xuất — mâu thuẫn với chính mục đích "ghi vết để đối soát". Đơn hàng cũ cũng còn
trỏ tới sản phẩm đó.

Đã bỏ hẳn endpoint xoá, thay bằng `PUT /products/{id}/hide` và `/unhide` — giống cách đã làm với
tài khoản (CN-08): khoá, không xoá. Cột `active` đã có sẵn nên không phải di trú gì.

Hàng đã ẩn biến mất khỏi cửa hàng với khách (VD-01, VD-24) nhưng nhân viên vẫn tra được và tồn kho
vẫn nguyên. Nút "Xoá" ở trang quản trị đổi thành "Ẩn khỏi cửa hàng" / "Bán lại".

### 🟢 VD-04. Chưa có giữ chỗ tồn kho

Kho trừ khi nhân viên xác nhận đơn (sau khi sửa VD-14). Hai khách cùng đặt món cuối cùng thì cả hai đều qua được
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

### ✅ VD-14. Xác nhận đơn không chắc còn hàng — đã sửa 24/09/2026

`checkout` hỏi tồn kho, nhưng tới lúc nhân viên xác nhận (mới thực sự trừ kho) thì hàng có thể
đã bán hết. Trước đây xác nhận chỉ phát sự kiện rồi trả về ngay: `product-service` ghi log lỗi
và bỏ qua, **đơn vẫn CONFIRMED trong khi kho không trừ** — hứa bán món không còn hàng.

**Đã sửa:** `order-service` gọi `POST /products/stock/deduct` ngay lúc xác nhận và **đợi kết
quả**, trong cùng transaction với việc đổi trạng thái đơn.

- Trừ cả đơn, tất-cả-hoặc-không. Thiếu một món là 409, không dòng nào bị trừ, đơn giữ nguyên
  `PENDING`, và nhân viên đọc được đúng món nào thiếu: *Sản phẩm "..." chỉ còn 0 gói, cần 1*.
- `product-service` tắt thì trả 503 "thử lại sau", đơn không được xác nhận — thà không chốt còn
  hơn chốt bán mà không biết còn hàng không.
- Gọi lại với cùng `orderId` không trừ hai lần (vết trong `stock_movements`). Nhờ vậy trường hợp
  hiếm "bên kia trừ xong nhưng bên này commit hỏng" chỉ cần bấm xác nhận lại.
- Endpoint mới chỉ mở cho STAFF/ADMIN, và `order-service` đính chính token của nhân viên đang
  bấm — chưa cần danh tính riêng giữa hai service (VD-24 vẫn còn cho `GET /products/{id}`).
- Sự kiện `order.completed` vẫn phát để service khác dùng; `product-service` nghe rồi bỏ qua vì
  đã trừ.

Kiểm chứng trên hệ thống thật: hai đơn cùng mua món cuối cùng, đơn sau nhận 409 và giữ nguyên
`PENDING`, vết kho chỉ có một dòng `SALE`.

**Còn lại:** vẫn chưa giữ chỗ lúc khách đặt hàng (VD-04) — hai khách vẫn đặt được cùng món cuối,
chỉ là người thứ hai bị từ chối lúc nhân viên xác nhận thay vì lúc đặt.

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

### ✅ VD-05. CN-07 — refresh token phát ra nhưng không dùng được — đã sửa 13/09/2026

`login` trả về `refreshToken` nhưng không endpoint nào nhận nó, nên cứ 15 phút người dùng bị
đăng xuất. Tệ hơn: refresh token cũ là **JWT ký cùng khoá với access token** và không lưu ở
đâu — không thu hồi được. Nó không dùng thay access token được chỉ vì **tình cờ** thiếu claim
`userId`.

**Đã sửa theo cách chuẩn:**

- Refresh token là 32 byte ngẫu nhiên, **không phải JWT** — không có đường nào lọt qua bộ lọc JWT.
- Server chỉ lưu **bản băm SHA-256** (bảng `refresh_tokens`, migration `V4`).
- **Xoay vòng** mỗi lần làm mới: token cũ bị thu hồi, sinh token mới.
- **Phát hiện đánh cắp:** token đã xoay vòng mà vẫn bị đem ra dùng thì thu hồi sạch mọi phiên
  của tài khoản, buộc đăng nhập lại bằng mật khẩu.
- Mỗi lần làm mới kiểm lại tài khoản còn tồn tại và còn `ACTIVE`.
- `POST /auth/refresh` và `POST /auth/logout`, cả hai không đòi access token.

Một chỗ dễ sai đã được chốt bằng test: lệnh thu hồi toàn bộ phiên xảy ra ngay trước khi ném
lỗi, mà `@Transactional` mặc định rollback khi có exception — tức là thu hồi xong lại bị huỷ
ngầm. Cần `noRollbackFor` ở **cả hai** tầng service. Test tương ứng chạy ngoài transaction, và
đã được kiểm bằng cách gỡ `noRollbackFor` ra: test đỏ.

**Frontend** tự làm mới ngầm khi gặp 401 rồi gửi lại request. Chỉ một lần làm mới chạy tại một
thời điểm — nếu ba request cùng làm mới thì request thứ hai sẽ dùng token vừa bị xoay vòng và
kích hoạt cơ chế phát hiện đánh cắp. Kiểm chứng trong trình duyệt thật: access token hết hạn,
trang bắn ba request cùng lúc, cả ba dính 401, **chỉ một** lần gọi `/auth/refresh`, người dùng
không thấy gì.

Đăng xuất giờ thu hồi refresh token ở server, không chỉ xoá ở máy.

### ✅ VD-06. CN-08 — không khoá được tài khoản — đã sửa 13/09/2026

`UserStatus` khai báo sẵn `LOCKED` nhưng không code nào set giá trị đó.

**Đã sửa:** `PUT /admin/users/{id}/lock` và `/unlock` (chỉ ADMIN). Giao diện quản trị dùng
**khoá thay cho xoá** — xoá hẳn làm lịch khám, bệnh án và đơn hàng cũ mất người liên quan, và
khách vẫn đặt được vào giờ trống của bác sĩ đã xoá. Trang Tài khoản không có nút xoá.

- Khoá thu hồi mọi refresh token. Đăng nhập đúng mật khẩu trả 403 "đã bị khoá"; sai mật khẩu
  vẫn 401 như mọi trường hợp khác, nên không dò được email nào bị khoá.
- Không tự khoá mình, không khoá admin đang hoạt động cuối cùng (409).
- Mở khoá tài khoản chưa xác minh email thì về lại `INACTIVE`, không lách được bước xác minh.
- Sự kiện `user.locked` / `user.unlocked` → booking-service: bác sĩ bị khoá thì slot trống từ
  hôm nay chuyển `BLOCKED`, không sinh slot mới, lịch bị huỷ không mở lại giờ đó. Mở khoá thì
  mở lại và sinh bù. Lịch đã đặt giữ nguyên — nhân viên tự liên hệ khách.

**Còn lại:**
- Access token đang cầm vẫn dùng được tới khi hết hạn (15 phút) — các service tự kiểm JWT,
  không hỏi lại auth-service mỗi request.
- Bác sĩ bị khoá vẫn hiện trong danh sách công khai `GET /profile/doctors` (profile-service chưa
  nghe sự kiện khoá). Không đặt được lịch với họ, nhưng trang giới thiệu vẫn liệt kê.
- `DELETE /admin/users/{id}` vẫn tồn tại ở backend, booking-service không nghe `user.deleted`.
- Chưa tự khoá khi đăng nhập sai nhiều lần.

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

### ✅ VD-10. Ranh giới `Pet` chồng lấn với `pet-service` — đã sửa 25/09/2026

Entity `Pet` nằm trong `profile-service` trong khi roadmap có `pet-service` riêng. Đã chọn tách
thật thay vì thu hẹp `pet-service`: thú cưng là thực thể trung tâm của phòng khám thú y — lịch hẹn,
bệnh án, đơn thuốc đều trỏ vào nó — nên để nó lẫn trong hồ sơ hành chính của khách là sai chỗ.

**Chặng 1 (xong):**

- `pet-service` mới, port `8091`, `pet_db` riêng (host `5440`). Chủ nuôi lưu bằng `owner_user_id`
  (userId của `auth-service`) chứ không phải `customer_profile_id`, nên `pet-service` không phụ
  thuộc vào cách `profile-service` lưu hồ sơ khách.
- `profile-service` bỏ hẳn phần thú cưng. Bảng cũ **không bị DROP**, chỉ đổi tên thành
  `pets_moved_to_pet_service_backup` (`V3__move_pets_to_pet_service.sql`) — dữ liệu đã chạy thật,
  giữ lại để đối chiếu.
- Di trú dữ liệu: `scripts/migrate-pets-to-pet-service.sh`, join qua `customer_profiles` để đổi
  khoá, **giữ nguyên `id` từng con vật** vì `booking_db` đang trỏ tới. Chạy lại nhiều lần được.
- `booking-service` gọi `PetServiceClient` thay cho `ProfileServiceClient`, truyền nguyên token của
  người dùng nên `pet-service` tự quyết quyền sở hữu.
- Bảng Khách hàng của admin giờ gọi hai service: điện thoại/địa chỉ từ `profile-service`, thú cưng
  từ `GET /pets/by-owners`. `CustomerSummaryResponse` không còn `petNames`.

**Chặng 2 (xong):**

- Bệnh án và đơn thuốc sang `pet-service`. Không còn khoá ngoại tới `appointments`: `appointment_id`
  là UUID thường, còn `pet_id` thì có khoá ngoại thật vì thú cưng ở ngay trong service này.
- `pet_id`, chủ nuôi và bác sĩ được chép vào bệnh án lúc lập, lấy từ `booking-service` chứ không từ
  client — tin body gửi lên thì một bác sĩ có thể gắn bệnh án vào con vật của người khác. Đọc bệnh
  án về sau không cần `booking-service`, nên hồ sơ lâm sàng tra được cả khi service kia tắt.
- `prescription.created` giờ phát lên exchange `pet.events`; `payment-service` chỉ đổi chỗ nghe, nội
  dung message không đổi. `payment.completed` cũng chuyển chỗ nghe từ `booking-service` sang
  `pet-service` (queue `pet.payment-completed`).
- Thú cưng đã có bệnh án thì không xoá được hồ sơ (409), và xoá tài khoản khách cũng không kéo theo
  con đó. Trước đây bệnh án nằm khác database nên không có gì chặn: xoá con vật là để lại bệnh án
  mồ côi không ai tra ra được nữa.
- Di trú: `scripts/migrate-medical-records-to-pet-service.sh`, join `appointments` và
  `appointment_slots` để lấy ba trường trên, giữ nguyên `id` bệnh án vì
  `payment_db.payments.medical_record_id` đang trỏ tới. Bệnh án của con vật không còn hồ sơ thì bị
  bỏ lại và báo số lượng, không làm đứt cả lượt di trú.

Việc còn lại của module này là CN-26 (nhắc tái khám, tiêm phòng) — chưa làm, không thuộc phần tách
service.

---

## booking-service

### ✅ VD-16. Lịch hẹn không cho biết bác sĩ nào khám — đã sửa 13/09/2026

Khách không chọn bác sĩ, hệ thống tự xếp slot — bản thân điều đó hợp lệ. Vấn đề là
`AppointmentResponse` và `AppointmentDetailResponse` chỉ trả `slotId`, không trả bác sĩ,
trong khi `GET /booking/appointments` lại **lọc** được theo `doctorUserId`. Lọc theo bác sĩ
thì được, hiện bác sĩ lên màn hình thì không.

**Đã sửa:** thêm `doctorUserId` vào cả hai response, lấy thẳng từ slot — không gọi thêm
service nào. Có 5 test trong `AppointmentDoctorTest`, mỗi test khoá một đường trả response
(tạo, danh sách của khách, tra cứu của lễ tân, chi tiết, huỷ và đổi trạng thái), cộng một
kiểm tra JSON trong test tự xếp bác sĩ có sẵn.

Frontend ghép `doctorUserId` với danh sách bác sĩ công khai:

- Lễ tân và quản trị có **cột bác sĩ** và **bộ lọc theo bác sĩ** trên bảng lịch khám.
- Khách đặt xong thấy ngay "Người khám" trên trang chi tiết, danh sách lịch và hồ sơ thú cưng.

**Chưa có tên người:** nhãn hiện là chuyên môn ("Bác sĩ nội khoa chó mèo"), vì tên chỉ nằm
trong bảng `users` của auth-service — xem VD-20. Khi backend trả tên thì chỉ sửa một hàm
`doctorLabel` trong `frontend/web/src/lib/useDoctors.ts`.

### ✅ VD-17. Bệnh án chỉ tra được theo lịch hẹn, không theo thú cưng — đã sửa 25/09/2026

Bệnh án chỉ truy cập được qua từng lịch hẹn, không có đường nào lấy lịch sử khám của **một thú
cưng** qua nhiều lần hẹn — đúng chức năng CN-24 và là thứ bác sĩ cần nhất khi khám: con vật này
trước đây bị gì, đã dùng thuốc nào.

Việc tách `pet-service` (VD-10 chặng 2) giải quyết luôn: bệnh án nằm cùng service với thú cưng và có
sẵn `pet_id`, nên chỉ là một câu lọc thường, không phải join ba bảng.

- `GET /pets/{petId}/medical-records` cho bác sĩ, nhân viên, admin.
- `GET /pets/me/{petId}/medical-records` cho khách — của chính con mình nuôi.

Trang hồ sơ thú cưng của khách giờ hiện chẩn đoán ngay dưới từng lần khám, không phải mở từng lịch
hẹn mới thấy.

---

## Toàn hệ thống

### ✅ VD-11. CN-22 và CN-39 chồng lấn — đã chốt 24/09/2026

Hai chức năng cùng nói về *ca trực của bác sĩ*, không chốt thì có hai nguồn sự thật.

**Đã chốt ngược với đề xuất cũ: `staff-service` giữ ca trực.** Ca trực là chuyện nhân sự (ai
đi làm hôm nào), còn khung giờ khám là hệ quả của nó. `booking-service` nghe sự kiện
`shift.added` / `shift.removed` và giữ một bản sao trong bảng `doctor_shifts` để sinh khung giờ
mà không phải gọi sang service khác — lượt sinh slot chạy nền, không có token người dùng nào.

Hệ quả thấy ngay: **bác sĩ không có ca thì ngày đó khách không đặt được lịch**. Trước đây mọi
bác sĩ đều có đủ khung giờ mọi ngày, kể cả ngày nghỉ.

CN-22 (cấu hình khung giờ) còn lại đúng phần giờ mở cửa và độ dài mỗi lượt khám — vẫn là hằng
số trong `ClinicSchedule` của booking-service.

### ✅ VD-12. Test tích hợp chạy nhầm vào database thật — đã sửa 24/09/2026

`auth-service` và `profile-service` không có `src/test/resources/application.yml` riêng, nên
`@SpringBootTest` dùng thẳng `application.yml` chính và cần DB thật đúng cổng. Chạy `mvn test`
trần sẽ hỏng nếu không set biến môi trường.

`product-service` đã đỡ hơn một phần: test controller tắt listener RabbitMQ nên không cần
broker, nhưng vẫn cần Postgres.

**Vì sao nguy hiểm:** một số test của `booking-service` không chạy trong transaction và dọn
bằng `deleteAll()` — `AppointmentEventPublisherTest` xoá **toàn bộ** lịch hẹn lẫn khung giờ.
Ngày 13/09 chạy nhầm bộ test này vào `booking_db` thật làm mất sạch lịch hẹn, phải khôi phục
từ bản sao lưu.

**Đã sửa bằng hai lớp:**

1. **Mặc định đúng.** Mỗi `pom.xml` khai `DB_PORT` và `DB_NAME` trỏ vào database `*_test` ngay
   trong `maven-surefire-plugin`. Cấu hình trong pom thắng cả biến môi trường lẫn `-D` trên
   dòng lệnh, nên `DB_NAME=booking_db mvn test` vẫn chạy vào `booking_db_test`.
2. **Chốt chặn lúc chạy.** `TestDatabaseGuard` (mỗi service một bản, đăng ký trong
   `src/test/resources/META-INF/spring.factories`) đọc `spring.datasource.url` và ném lỗi nếu
   tên database không kết thúc bằng `_test`. Chặn trước khi Spring mở kết nối đầu tiên, và bắt
   cả trường hợp test tự khai `spring.datasource.url` trong `@SpringBootTest`.

Cũng đặt luôn `JWT_SECRET` riêng cho test và múi giờ `Asia/Ho_Chi_Minh` (VD-25), nên chạy test
trong IDE không phải khai gì thêm.

```bash
bash scripts/create-test-databases.sh   # một lần, sau khi docker compose up -d
bash scripts/test.sh                    # tất cả service
bash scripts/test.sh booking-service    # một service
```

Script `test.sh` chỉ nạp mật khẩu database và RabbitMQ từ `.env`; phần chọn database test nằm
trong pom nên chạy `mvn test` trực tiếp cũng an toàn.

**Còn lại:** vẫn cần Postgres thật đang chạy (chưa dùng Testcontainers), và
`ProfileServiceClientTest` của booking-service vẫn phải có Eureka + profile-service thật nên
đã loại khỏi lượt chạy thường.

### ⚪ VD-13. Gateway trả 503 khoảng 30 giây sau khi rebuild

Spring Cloud LoadBalancer cache danh sách instance từ Eureka theo chu kỳ. Không phải lỗi,
nhưng dễ làm mất công debug nhầm. Đã ghi trong `frontend/README.md`.

### 🟡 VD-18. Hai mảng giao diện quản trị chưa có API (còn lại sau 24/09)

Rà khi dựng frontend Next.js. Các màn hình dưới đây không có endpoint nào phục vụ:

| Màn hình | Thiếu gì |
|---|---|
| Thông báo trong ứng dụng | `notification-service` không có REST endpoint |

Frontend đang để placeholder có đánh dấu `TODO` và tầng mock riêng (`lib/api/mock/`), không
nhúng dữ liệu giả vào component (`lib/api/missing.ts`). Khi API có thật thì chỉ thay tầng đó.

Đã gỡ ngày 13/09: số liệu tổng quan và biểu đồ báo cáo (`reporting-service`, CN-46/47/49); danh sách
tài khoản, khách hàng và tên nhân viên (`GET /admin/users?role=&status=&keyword=&page=&size=` ở
auth-service, chỉ ADMIN). Trang Khách hàng ghép thêm điện thoại, địa chỉ và thú cưng qua
`GET /profile/customers/summary?userIds=` (chỉ ADMIN, tối đa 100 khách mỗi lần).


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


### ✅ VD-20. `GET /profile/doctors` không trả họ tên bác sĩ — đã sửa 13/09/2026

Họ tên chỉ nằm trong bảng `users` của auth-service, còn trang công khai và lịch hẹn lấy thông
tin bác sĩ từ profile-service. Không trang nào hiện được tên người khám.

**Đã sửa bằng sự kiện**, không gọi đồng bộ giữa service:

- Admin tạo tài khoản DOCTOR/STAFF → auth-service phát `user.staff-created` kèm họ tên
  (thứ tự Việt Nam: biểu mẫu đặt "Họ" vào firstName, "Tên" vào lastName).
- profile-service nhận sự kiện, tạo sẵn hồ sơ bác sĩ có `full_name` (migration `V2`). Bác sĩ
  mới có mặt trong danh sách công khai ngay, không phải đợi họ tự mở trang hồ sơ lần đầu.
- `DoctorPublicResponse` trả thêm `fullName`. Frontend hiện "BS. Họ Tên"; hồ sơ chưa có tên
  thì rơi về chuyên môn.

Test: `StaffAccountCreatedEventTest` (auth), `DoctorNameTest` (profile — gồm nhận trùng sự kiện
không tạo trùng hồ sơ và không xoá chuyên môn đã khai). Kiểm chứng đầu-cuối: admin tạo bác sĩ
mới, tên xuất hiện trong danh sách công khai qua RabbitMQ.

**Dữ liệu cũ:** hồ sơ lập trước `V2` có `full_name` rỗng — hai database khác nhau nên migration
không tự điền được. Môi trường dev đã điền tay cho ba bác sĩ demo. Khi triển khai thật cần chạy
một lần: lấy `first_name || ' ' || last_name` từ `auth_db.users` ghi vào
`profile_db.doctor_profiles.full_name` theo `user_id`.

**Còn lại:** chưa có ảnh chân dung (`photoUrl`).

### 🟡 VD-21. `AppointmentRequest` không có trường dịch vụ

Phát hiện khi dựng luồng đặt lịch cho khách.

Khách chỉ gửi được `petId`, `date`, `startTime`, `reason`. Không có cách nào nói "tôi cần
tiêm phòng" hay "tôi cần triệt sản" thành một mục có cấu trúc — trong khi đó chính là thứ
quyết định phòng khám cần chuẩn bị gì và ca kéo dài bao lâu.

Cũng không có service nào quản lý danh mục dịch vụ, nên bốn dịch vụ hiện trên trang chủ là
nội dung tĩnh trong `frontend/web/src/config/clinic.ts`.

Frontend tạm hướng dẫn khách ghi vào ô lý do khám.

**Hướng sửa:** một bảng `services` (tên, mô tả, thời lượng, giá tham khảo) và thêm
`serviceId` vào `AppointmentRequest`. Thời lượng còn dùng để chia slot cho đúng — hiện mọi
ca đều cố định 30 phút bất kể làm gì.

### ✅ VD-22. `PetResponse` thiếu ảnh, dị ứng và ghi chú — đã sửa 25/09/2026 (trừ ảnh)

Phát hiện khi dựng trang hồ sơ thú cưng cho khách.

`Pet` hiện có: tên, loài, giống, giới tính, ngày sinh, cân nặng. Thiếu ba thứ mà chủ nuôi
lẫn bác sĩ đều cần:

- `photoUrl` — trong danh sách nhiều bé, ảnh phân biệt nhanh hơn chữ. Frontend đang dùng
  mái vòm màu theo loài kèm hình con vật tương ứng để thay thế.
- `allergies` — dị ứng thuốc là thông tin an toàn, phải đập vào mắt bác sĩ trước khi kê đơn.
- `notes` — thói quen, tính nết, những thứ dặn người khám.

Đã thêm `allergies` và `notes` vào `PetRequest`/`PetResponse` (`V3` của `pet_db`). Ô để trống lưu
thành `null` chứ không phải chuỗi rỗng — "chưa khai" khác hẳn "đã khai là không có gì".

`allergies` theo được tới tận màn hình khám: `booking-service` trả kèm trong chi tiết lịch hẹn, và
bác sĩ thấy nó trong một khối viền vàng riêng ngay trên chỗ kê đơn, không nằm lẫn trong danh sách
cân nặng, giới tính.

**Còn `photoUrl`:** chưa làm. Dán link ảnh thì thực tế không ai có sẵn link, còn tải ảnh lên thật
thì cần thêm chỗ lưu file (MinIO hoặc thư mục gắn vào container) — một hạ tầng mới cho cả dự án,
để riêng một lần khác. Giao diện vẫn dùng mái vòm màu theo loài.

### 🟢 VD-23. Bệnh án chưa có trả 404, trình duyệt vẫn ghi lỗi ra console

Không phải lỗi chức năng. `GET /medical-records/by-appointment/{id}` trả 404 khi bác
sĩ chưa lập bệnh án — frontend bắt và hiểu đúng là "chưa có", nhưng trình duyệt vẫn ghi một
dòng 404 đỏ vào console. Ai mở DevTools lên xem sẽ tưởng có lỗi.

**Hướng sửa (khi rảnh):** trả 200 kèm thân rỗng, hoặc thêm `GET .../exists`.
Không gấp.

### ✅ VD-24. Xem được hàng đã ẩn nếu biết UUID — đã sửa 24/09/2026

Phát hiện khi sửa VD-01. `order-service` gọi `GET /products/{id}` như khách vãng lai, nên
endpoint đó phải trả cả hàng đã ẩn để giỏ hàng biết món nào ngừng bán — và ai có UUID cũng đọc
được hàng chưa bán hoặc đã ngừng bán.

**Đã sửa mà không cần danh tính service:** `GET /products/{id}` giờ trả **404** cho hàng đã ẩn,
trừ khi người gọi là STAFF/ADMIN. Trả 404 chứ không 403 — 403 là tự xác nhận "có sản phẩm này,
chỉ không cho xem", đủ để dò danh mục hàng sắp bán.

Giỏ hàng không hỏng: `order-service` vốn đã xử lý 404 sẵn. Món bị ẩn hiện thành
"(sản phẩm không còn bán)", không mua được, và chặn đặt đơn. Đổi lại, khách mất tên món trong
giỏ — chấp nhận được, vì tên hàng đã ngừng bán không phải thứ khách cần biết.

Kiểm chứng trên hệ thống thật: sau khi admin ẩn một sản phẩm đang nằm trong giỏ của khách —
khách vãng lai và khách hàng tra UUID đều nhận 404, nhân viên và quản trị vẫn xem được; giỏ
hàng chặn đặt; thêm lại món đó vào giỏ trả 409 "Sản phẩm này không còn bán". Trang sản phẩm
phía khách hiện "Không tìm thấy sản phẩm này".

**Ghi chú:** cách này dựa vào việc `order-service` không cần thấy hàng ẩn. Nếu sau này có luồng
service gọi nhau thật sự cần quyền cao hơn người dùng cuối, khi đó mới phải làm danh tính riêng
giữa các service.

### ✅ VD-25. Test tích hợp lỗi trên Windows vì múi giờ `Asia/Saigon` — đã sửa 24/09/2026

JVM trên Windows gửi múi giờ tên cũ `Asia/Saigon`, PostgreSQL 16 từ chối:
`FATAL: invalid value for parameter "TimeZone"`. Mọi test dùng database đều lỗi ngay lúc
dựng context, trông như code hỏng trong khi code không sai gì.

**Đã sửa:** `<argLine>-Duser.timezone=Asia/Ho_Chi_Minh</argLine>` nằm trong
`maven-surefire-plugin` của mọi `pom.xml`, không ai phải nhớ cờ này nữa. Truyền
`-DargLine=...` trên dòng lệnh sẽ KHÔNG ghi đè được cấu hình pom — muốn đổi thì sửa pom.

