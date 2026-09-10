# Frontend

Giao diện web của hệ thống. Gồm **hai mặt cố tình khác nhau**:

- **Website khách hàng** (`app/(site)/`) — mặt tiền thương mại ở gốc `/`. Giới thiệu phòng
  khám, bán hàng, đặt lịch khám. Đầu trang kiểu công ty, không có thanh bên quản trị.
  Bản thiết kế và lý do từng lựa chọn nằm ở `docs/thiet-ke-website-khach-hang.md`.
- **Dashboard nội bộ** (`app/(dashboard)/`) — công cụ làm việc của bác sĩ, nhân viên và
  quản trị, thanh bên cố định, dày đặc thông tin.

Hai mặt dùng chung tầng API, tầng xác thực và bộ kiểu dữ liệu; khác nhau ở bảng màu, bộ
chữ và bộ phần tử giao diện.

- **Công nghệ:** Next.js 16 (App Router) · TypeScript · Tailwind CSS 4 · TanStack Query ·
  React Hook Form + Zod · Recharts · Lucide
- **Mã nguồn:** `web/`
- **Cổng:** 3000 khi chạy bằng `docker compose`, 3001 khi chạy ở máy

> `test-ui/` — trang thử API bằng HTML thuần — đã bị gỡ bỏ khi giao diện thật thay thế nó.
> Lịch sử vẫn còn trong git nếu cần tra lại.

## Chạy ở máy

```bash
cd frontend/web
npm install
npm run dev          # http://localhost:3001
```

Cần backend chạy sẵn (`docker compose up` ở thư mục gốc). Next gọi gateway qua biến
`API_GATEWAY_URL`, mặc định `http://localhost:8080`.

Nếu `npm run dev` báo lỗi `0xc0000142` trên Windows thì máy đang cạn tiến trình — tắt bớt
container hoặc dùng bản production:

```bash
npm run build && npm start
```

## Vì sao mọi request đi qua `/api/*`

Trình duyệt gọi thẳng `localhost:8080` sẽ dính CORS. Thay vì mở CORS ở backend, Next nhận
request tại `/api/*` của chính nó rồi chuyển tiếp sang gateway (`next.config.ts`) — cùng
origin với trang nên không có yêu cầu preflight nào.

Trong Docker, `API_GATEWAY_URL` là `http://api-gateway:8080`: các container gọi nhau bằng
tên service, không phải `localhost`.

## Cấu trúc

```
web/src/
├── app/
│   ├── (auth)/           đăng nhập, đăng ký, xác minh email
│   ├── (site)/           website khách hàng: /, /products, /cart, /pets,
│   │                     /appointments, /orders
│   ├── (dashboard)/      admin · doctor · staff
│   └── unauthorized/
├── components/
│   ├── site/             bộ phần tử của website khách hàng
│   ├── ui/               bộ phần tử của dashboard: Button, DataTable, Dialog…
│   ├── layout/           Sidebar, DashboardShell
│   └── dashboard/        Metric
├── lib/
│   ├── api/              một file cho mỗi service backend
│   ├── auth/             AuthProvider, RouteGuard
│   └── utils/            format tiền/ngày, nhãn trạng thái
├── config/
│   ├── nav.ts            menu dashboard theo vai trò
│   └── clinic.ts         nội dung thương hiệu của website khách hàng
└── types/                khớp 1-1 với DTO backend
```

## Phân quyền

`RouteGuard` chặn ba nhánh dashboard `/admin`, `/doctor`, `/staff` theo vai trò đọc từ JWT.
Bên website khách hàng, các trang cần đăng nhập (`/cart`, `/pets`, `/appointments`,
`/orders`) tự bọc `<CustomerOnly>`; `/` và `/products` mở cho cả khách vãng lai.

Đây **chỉ là lớp trải nghiệm** — backend vẫn kiểm quyền trên từng request, và mọi endpoint
đều tự trả 401/403.

`ready` trong `AuthProvider` lấy qua `useSyncExternalStore` chứ không phải
`typeof window !== "undefined"`. Nghe vụn vặt nhưng đó là khác biệt giữa chạy được và
không: lần commit đầu tiên trên trình duyệt vẫn dùng ảnh chụp phía máy chủ, nên nếu `ready`
đã true trong khi token còn null thì `RouteGuard` đá người đang đăng nhập về trang đăng
nhập — chỉ khi họ mở thẳng một địa chỉ cần quyền, không lộ khi bấm chuyển trang.

Không có endpoint làm mới token (xem `docs/van-de-ton-dong.md` — VD-05), nên khi access
token hết hạn sau 15 phút, mọi 401 sẽ đưa người dùng về trang đăng nhập kèm `?next=` để
quay lại đúng chỗ đang dở.

## Màn hình chưa có dữ liệu

Một số mục trong menu có **chấm vàng**: backend chưa có API cho chúng (VD-18). Các trang đó
hiển thị đúng endpoint còn thiếu thay vì dựng số liệu giả. Khai báo tập trung ở
`lib/api/missing.ts`; khi backend bổ sung endpoint thì sửa ở đó, không phải sửa component.

## Lưu ý khi vừa `docker compose up --build`

API Gateway trả `503` trong khoảng 30 giây đầu vì Spring Cloud LoadBalancer còn chờ Eureka
đẩy danh sách instance xuống. Giao diện hiểu mã này và hiện "Dịch vụ đang khởi động, thử lại
sau vài giây" thay vì báo hỏng.

Trên Windows, nếu gõ thẳng `localhost:8080` mà không vào được thì dùng `127.0.0.1:8080` —
`localhost` phân giải sang IPv6 còn Docker chỉ publish trên IPv4.
