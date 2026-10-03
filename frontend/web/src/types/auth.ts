export type Role = "CUSTOMER" | "DOCTOR" | "STAFF" | "ADMIN";

/** auth-service: UserStatus. Khách tự đăng ký là INACTIVE cho tới khi bấm link xác minh email. */
export type UserStatus = "ACTIVE" | "INACTIVE" | "LOCKED";

/** auth-service: AccessTokenResponse. Refresh token không còn ở đây — nó nằm trong cookie
 * httpOnly do backend set, JS không đọc được (xem VD-05). */
export interface AccessTokenResponse {
  accessToken: string;
}

/** auth-service: UserResponse, trả về từ GET /auth/me và từng dòng của GET /admin/users. */
export interface CurrentUser {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  status: UserStatus;
  roles: Role[];
  createdAt: string;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  confirmPassword: string;
}

/** Bộ lọc của GET /admin/users (chỉ ADMIN). Mọi field tuỳ chọn; size tối đa 100. */
export interface UserFilters {
  role?: Role;
  status?: UserStatus;
  keyword?: string;
  page?: number;
  size?: number;
}

/** auth-service: CreateStaffAccountRequest. ADMIN tạo tài khoản DOCTOR/STAFF. */
export interface CreateStaffAccountRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  role: Extract<Role, "DOCTOR" | "STAFF">;
}

/**
 * Payload JWT. Chỉ dùng để hiển thị và ẩn/hiện menu — quyền thật do backend quyết.
 * `roles` là tên trần ("ADMIN"), phía service mới tự thêm tiền tố ROLE_.
 */
export interface TokenClaims {
  sub?: string;
  userId?: string;
  roles?: Role[];
  exp?: number;
}
