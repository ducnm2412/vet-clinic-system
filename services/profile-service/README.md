# Profile Service

Hồ sơ chi tiết của 3 actor: Khách hàng (địa chỉ + thú cưng), Bác sĩ (chuyên khoa + bằng cấp), Nhân viên. Không xử lý đăng nhập/mật khẩu — danh tính xác thực do `auth-service` quản lý, `profile-service` chỉ liên kết qua `userId` (UUID) lấy từ claim trong JWT.

- **Database riêng:** `profile_db` (PostgreSQL) — theo mô hình Database per Service
- **Port:** `8083` (`SERVER_PORT`)

## Kiến trúc

### Vị trí trong hệ thống

```
Client → API Gateway → profile-service → profile_db (PostgreSQL)
                             ↑
                       Eureka Server (service discovery)

auth-service phát JWT (kèm claim userId) → profile-service tự verify
bằng chung JWT_SECRET, KHÔNG gọi ngược auth-service mỗi request.
```

### Package structure

```
com.vetclinic.profile
├── controller        # CustomerProfileController, DoctorProfileController, StaffProfileController
├── domain            # CustomerProfile, Address, Pet, DoctorProfile, DoctorLicense, StaffProfile
├── dto                # Request/Response record theo từng actor
├── exception          # ResourceNotFoundException + GlobalExceptionHandler
├── repository         # Spring Data JPA, mỗi repo có findByIdAndXxxProfileId để check ownership
├── security
│   └── jwt           # JwtUtil (CHỈ verify — không generate), JwtAuthFilter, JwtProperties, AuthenticatedUser
│   SecurityConfig     # RBAC theo route prefix
└── service            # CustomerProfileService, DoctorProfileService, StaffProfileService
```

### Vì sao security "chỉ verify"

`profile-service` không phát hành token — nó copy nguyên `JwtUtil`/`JwtAuthFilter`/`JwtProperties` từ `auth-service` nhưng **bỏ hết phần `generateAccessToken`/`generateRefreshToken`**, chỉ giữ `parseClaims`/`extractRoles`/`extractUserId`. Cấu hình `jwt.secret` dùng **chung giá trị** với `auth-service` (biến `JWT_SECRET` giống hệt) để chữ ký khớp nhau — đây là cách các service con tự xác thực JWT mà không tạo phụ thuộc mạng vào `auth-service` ở mỗi request.

### Lazy-create profile

`CustomerProfile`/`DoctorProfile`/`StaffProfile` **không có endpoint "tạo mới"** riêng — `GET /profile/*/me` lần đầu tiên tự tạo row rỗng gắn với `userId` trong token, các lần sau trả về row đã có. Vì vậy **không có** exception "ProfileNotFound" — profile luôn tồn tại theo yêu cầu.

## Domain model

| Entity | Field chính | Quan hệ |
|---|---|---|
| `CustomerProfile` | `userId` (unique), `phone`, `dateOfBirth` | 1-n `Address`, 1-n `Pet` |
| `Address` | `line1`, `line2`, `ward`, `city`, `isDefault` | n-1 `CustomerProfile`, cascade xoá theo profile |
| `Pet` | `name`, `species`, `breed`, `gender`, `dateOfBirth`, `weightKg` | n-1 `CustomerProfile`, cascade xoá theo profile |
| `DoctorProfile` | `userId` (unique), `specialty`, `phone`, `bio`, `yearsOfExperience` | 1-n `DoctorLicense` |
| `DoctorLicense` | `licenseNumber`, `issuedBy`, `issuedDate`, `expiryDate` | n-1 `DoctorProfile`, cascade xoá theo profile |
| `StaffProfile` | `userId` (unique), `position`, `phone`, `hireDate` | — |

`Pet` **không có** hồ sơ bệnh án (tiêm phòng/chẩn đoán/điều trị) — đó là dữ liệu lâm sàng phát sinh theo từng lượt khám, thuộc về `booking-service` (chưa code), tham chiếu ngược `petId` sang đây. `profile-service` chỉ giữ định danh tĩnh của thú cưng.

### Schema (Flyway)

`V1__create_profile_tables.sql` — tạo cả 6 bảng **cùng lúc trong 1 migration** (khác `auth-service` vì đây làm 1 lần lúc khởi tạo, không cần tách nhiều version). `ddl-auto: validate`, Flyway là nguồn sự thật duy nhất cho schema.

## Chức năng / API

| Method | Endpoint | Quyền truy cập | Mô tả |
|---|---|---|---|
| `GET/PUT` | `/profile/customer/me` | `ROLE_CUSTOMER` | Xem/sửa hồ sơ khách hàng (lazy-create) |
| `GET/POST/PUT/DELETE` | `/profile/customer/me/addresses[/{id}]` | `ROLE_CUSTOMER` | CRUD địa chỉ — chỉ 1 địa chỉ `isDefault=true` tại 1 thời điểm |
| `GET/POST/PUT/DELETE` | `/profile/customer/me/pets[/{id}]` | `ROLE_CUSTOMER` | CRUD thú cưng |
| `GET` | `/profile/customer/by-id/{customerProfileId}` | `ROLE_STAFF` hoặc `ROLE_ADMIN` | Tra cứu hồ sơ 1 khách hàng cụ thể (vd nhân viên lễ tân cần xem khi khách gọi điện) — path tách biệt hẳn `/me` để không đụng rule phân quyền |
| `GET/PUT` | `/profile/doctor/me` | `ROLE_DOCTOR` | Xem/sửa hồ sơ bác sĩ (lazy-create) |
| `GET/POST/PUT/DELETE` | `/profile/doctor/me/licenses[/{id}]` | `ROLE_DOCTOR` | CRUD bằng cấp/giấy phép hành nghề |
| `GET` | `/profile/doctors` | **Public** | Danh sách bác sĩ (cho trang đặt lịch) — DTO riêng, không lộ `phone` |
| `GET/PUT` | `/profile/staff/me` | `ROLE_STAFF` hoặc `ROLE_ADMIN` | Xem/sửa hồ sơ nhân viên (lazy-create) |
| `GET` | `/profile/staff` | `ROLE_ADMIN` | Danh sách toàn bộ nhân viên |

### Business rules đáng chú ý

- **Ownership check ở mọi thao tác con-resource**: sửa/xoá `Address`/`Pet`/`DoctorLicense` đều query bằng `findByIdAndXxxProfileId(id, profile.getId())` — user A không thể đụng vào resource của user B dù biết đúng UUID (chống IDOR), trả 404 thay vì 403 để không lộ resource đó có tồn tại hay không.
- **Ràng buộc `isDefault` chỉ 1 địa chỉ**: khi tạo/sửa 1 địa chỉ thành `isDefault=true`, các địa chỉ khác của cùng profile tự động bị bỏ default (`clearOtherDefaultAddresses`).
- **`GET /profile/doctors` public nhưng `GET /profile/doctor/me` thì không** — dù cùng tiền tố `/profile/doctor`, đây là 2 khái niệm khác nhau: danh sách công khai (ai xem cũng được) vs hồ sơ của chính mình (bắt buộc phải biết "mình" là ai qua token). Tương tự `GET /profile/staff` (danh sách, Admin) khác `/profile/staff/me` (chính mình, Staff/Admin).
- **`saveAndFlush` thay vì `save`** ở mọi thao tác tạo/sửa: bug từng gặp lúc test — Hibernate chỉ điền `@CreationTimestamp`/`@UpdateTimestamp` lúc flush, nếu không flush ngay thì response trả về ngay sau khi tạo/sửa sẽ có `createdAt`/`updatedAt` sai (null hoặc cũ).

## Cấu hình (biến môi trường)

| Biến | Mặc định | Ghi chú |
|---|---|---|
| `SERVER_PORT` | `8083` | |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `profile_db` | Qua `docker-compose`, `DB_HOST` override thành `profile-db` |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | |
| `EUREKA_URI` | `http://localhost:8761/eureka/` | |
| `JWT_SECRET` | **bắt buộc, không có default** | **Phải giống hệt** `JWT_SECRET` của `auth-service` |

Xem `.env.example` ở gốc repo (`PROFILE_DB_USER`/`PROFILE_DB_PASSWORD`).

## Chạy thử

```bash
export DB_HOST=localhost DB_PORT=5432 JWT_SECRET=<giống-auth-service>
mvn spring-boot:run
```

Qua Docker Compose: `docker compose up -d eureka-server profile-db rabbitmq api-gateway profile-service`. Gọi qua gateway (`http://localhost:8080/profile/...`) thay vì gọi thẳng `:8083`.

## Test

```bash
mvn test
```

55 test tự động, chạy với Postgres thật:
- `*ServiceTest` (Customer/Doctor/Staff) — lazy-create, CRUD, ownership isolation
- `*ControllerTest` — HTTP đầy đủ qua `MockMvc`, security filter chain chạy thật. Vì `JwtUtil` ở đây không có hàm generate, test dùng `TestJwtSupport` (tự dựng JWT bằng JJWT + secret đọc từ `@Value("${jwt.secret}")`) để mô phỏng token thật từ `auth-service`
- `*DtoValidationTest` — Bean Validation thuần cho từng DTO

## Hạn chế hiện tại / việc còn thiếu

- **Pet chưa có hồ sơ bệnh án** — dữ liệu lâm sàng theo lượt khám thuộc về `booking-service` (chưa code), sẽ tham chiếu `petId` sang đây
