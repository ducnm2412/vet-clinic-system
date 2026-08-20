# Auth Service

Xác thực (đăng ký/đăng nhập), quản lý JWT và phân quyền (RBAC) cho toàn hệ thống. Mọi service khác tin tưởng JWT do service này phát hành thay vì tự quản lý danh tính người dùng.

- **Database riêng:** `auth_db` (PostgreSQL) — theo mô hình Database per Service
- **Actor:** Khách hàng (tự đăng ký), Bác sĩ/Nhân viên (do Admin tạo), Admin (seed sẵn 1 tài khoản mặc định)
- **Port:** `8081` (`SERVER_PORT`)

## Kiến trúc

### Vị trí trong hệ thống

```
Client → API Gateway → auth-service → auth_db (PostgreSQL)
                            ↑
                      Eureka Server (service discovery)
```

`auth-service` tự đăng ký với Eureka lúc khởi động; `api-gateway` tra Eureka để route `/auth/**` và `/admin/**` tới `auth-service` qua `lb://auth-service` thay vì địa chỉ cố định.

### Package structure

```
com.vetclinic.auth
├── config          # AdminAccountSeeder — seed tài khoản Admin mặc định lúc khởi động
├── controller       # AuthController, AdminController — tầng HTTP, không chứa business logic
├── domain           # User, Role, RoleName, UserStatus — entity JPA
├── dto              # Request/Response record — tách biệt hình dạng API khỏi entity DB
├── exception         # Exception nghiệp vụ + GlobalExceptionHandler (@RestControllerAdvice)
├── repository        # UserRepository, RoleRepository (Spring Data JPA)
├── security
│   └── jwt          # JwtUtil (sinh/verify token), JwtAuthFilter, JwtProperties
│   SecurityConfig   # Cấu hình Spring Security: stateless, permitAll, RBAC theo route
└── service           # AuthService — toàn bộ business logic
```

Luồng xử lý 1 request: `Controller` (nhận request, validate `@Valid`) → `Service` (business logic, transaction) → `Repository` (truy vấn DB). `JwtAuthFilter` chạy trước tất cả, giải mã token và nạp `Authentication` vào `SecurityContext` nếu có.

### Vì sao thiết kế stateless

JWT tự mang danh tính (subject = email) và role trong claims. `JwtAuthFilter` **không tra DB** ở mỗi request — chỉ verify chữ ký + hạn dùng của token. Điều này giúp:
- Không cần session lưu ở server → scale nhiều instance dễ dàng, không cần sticky session
- Các service khác (qua API Gateway) tự verify được JWT mà không cần gọi ngược lại `auth-service`

Đánh đổi: nếu role của user đổi (vd Admin khoá tài khoản), thay đổi đó chỉ có hiệu lực ở lần **login tiếp theo** — token cũ đã phát hành vẫn dùng được tới khi hết hạn (15 phút mặc định).

## Domain model

| Entity | Field chính | Ghi chú |
|---|---|---|
| `User` | `id` (UUID), `firstName`, `lastName`, `email` (unique), `passwordHash`, `status`, `roles`, `verificationToken`, `verificationTokenExpiresAt`, `createdAt`, `updatedAt` | `roles` là quan hệ many-to-many qua bảng `user_roles` |
| `Role` | `id`, `name` (`RoleName`) | Dữ liệu tham chiếu tĩnh, seed sẵn 4 dòng |
| `RoleName` (enum) | `CUSTOMER`, `DOCTOR`, `STAFF`, `ADMIN` | |
| `UserStatus` (enum) | `ACTIVE`, `INACTIVE`, `LOCKED` | `INACTIVE` = chưa verify email; `LOCKED` định nghĩa sẵn cho tương lai (chưa có endpoint khoá tài khoản) |

### Schema (Flyway — `src/main/resources/db/migration`)

| Migration | Nội dung |
|---|---|
| `V1__create_users_and_roles.sql` | Tạo bảng `roles`, `users`, `user_roles`; seed 4 role |
| `V2__add_name_to_users.sql` | Thêm `first_name`, `last_name` |
| `V3__add_email_verification_token.sql` | Thêm `verification_token`, `verification_token_expires_at` + unique index (partial, cho phép nhiều `NULL`) |

Hibernate chạy ở chế độ `ddl-auto: validate` — **không** tự sinh/sửa bảng, Flyway là nguồn sự thật duy nhất cho schema.

## Chức năng / API

| Method | Endpoint | Quyền truy cập | Mô tả |
|---|---|---|---|
| `POST` | `/auth/register` | Public | Khách hàng tự đăng ký. Tạo user `status=INACTIVE`, role `CUSTOMER`, sinh token xác minh, **chưa** trả JWT |
| `GET` | `/auth/verify-email?token=` | Public | Xác minh email bằng token (dùng 1 lần, hạn 24h) → chuyển `status=ACTIVE` |
| `POST` | `/auth/login` | Public | Đăng nhập, trả `accessToken` + `refreshToken`. Chặn nếu `status != ACTIVE` |
| `GET` | `/auth/me` | JWT hợp lệ | Trả hồ sơ user hiện tại (tra DB theo email trong token) |
| `POST` | `/admin/users` | JWT + role `ADMIN` | Admin tạo tài khoản Bác sĩ/Nhân viên — `status=ACTIVE` ngay, không cần verify email |

### Business rules đáng chú ý

- **Đăng ký Customer bắt buộc verify email** trước khi login được — vì `JwtAuthFilter` không tra DB mỗi request, nếu phát JWT ngay lúc đăng ký thì yêu cầu verify sẽ vô nghĩa (token vẫn dùng được dù chưa xác minh). Do đó `register()` chỉ trả message, không trả token.
- **Tài khoản Doctor/Staff chỉ do Admin tạo**, không có form tự đăng ký công khai — tránh leo thang đặc quyền (client tự đặt `role=ADMIN` lúc đăng ký).
- **`POST /admin/users` chỉ nhận `role=DOCTOR` hoặc `STAFF`** — validate chặn cả `CUSTOMER` lẫn `ADMIN` qua endpoint này.
- **Lỗi đăng nhập không phân biệt** "email không tồn tại" / "sai password" / "tài khoản chưa verify" — cùng trả 401 với message chung, tránh lộ thông tin email nào đã đăng ký (chống user enumeration).
- **1 tài khoản Admin được seed tự động** lúc khởi động (`ADMIN_EMAIL`/`ADMIN_PASSWORD`), idempotent — không tạo trùng nếu email đã tồn tại.

## Bảo mật

- Password hash bằng **BCrypt** (`PasswordEncoder` bean trong `SecurityConfig`)
- JWT ký bằng HMAC (`JwtUtil`, thư viện JJWT 0.12.x), thuật toán tự chọn theo độ dài secret (HS256/384/512)
- `access token`: hạn 15 phút mặc định; `refresh token`: hạn 7 ngày (chưa có endpoint dùng refresh token để cấp lại access token — xem phần Hạn chế)
- `SecurityConfig`: CSRF tắt (API thuần JWT, không dùng cookie/session), `SessionCreationPolicy.STATELESS`
- Tất cả lỗi trả JSON có cấu trúc (`GlobalExceptionHandler`), không lộ stacktrace

## Cấu hình (biến môi trường)

| Biến | Mặc định | Ghi chú |
|---|---|---|
| `SERVER_PORT` | `8081` | |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `auth_db` | Khi chạy qua `docker-compose`, `DB_HOST` được override thành `auth-db` |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | |
| `EUREKA_URI` | `http://localhost:8761/eureka/` | |
| `JWT_SECRET` | **bắt buộc, không có default** | HS256 cần ≥32 byte. Tạo bằng `openssl rand -base64 64` |
| `JWT_ACCESS_EXPIRATION` / `JWT_REFRESH_EXPIRATION` | `900000` / `604800000` (ms) | 15 phút / 7 ngày |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | rỗng (bỏ qua seed) | Tài khoản Admin mặc định |

Xem `.env.example` ở gốc repo.

## Chạy thử

### Local (cần Postgres sẵn, không qua Docker)

```bash
export DB_HOST=localhost DB_PORT=5432 JWT_SECRET=<secret-du-32-byte>
mvn spring-boot:run
```

### Qua Docker Compose (khuyến nghị — chạy kèm `eureka-server`, `api-gateway`)

```bash
cp .env.example .env   # điền JWT_SECRET, AUTH_DB_PASSWORD thật
docker compose up -d eureka-server auth-db auth-service api-gateway
```

Gọi qua gateway: `http://localhost:8080/auth/register` (thay vì gọi thẳng `:8081`).

## Test

```bash
mvn test
```

24 test tự động, chạy với Postgres thật (không mock DB):

- `AuthServiceTest`, `AdminController`/`AuthControllerTest` — business logic + HTTP layer (`MockMvc`, security filter chain chạy thật)
- `RegisterRequestValidationTest`, `CreateStaffAccountRequestValidationTest` — Bean Validation thuần, không cần Spring context

## Hạn chế hiện tại / việc còn thiếu

- **Chưa gửi email thật** — link xác minh chỉ log ra console server (`AuthService.sendVerificationEmail`). Cần publish message lên RabbitMQ để `notification-service` (chưa code) gửi email thật.
- **Chưa có endpoint dùng `refreshToken`** để cấp lại `accessToken` khi hết hạn — client phải login lại.
- **Chưa có endpoint khoá tài khoản** (`status=LOCKED`) dù enum đã định nghĩa sẵn.
