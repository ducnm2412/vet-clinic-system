export type Role = "CUSTOMER" | "DOCTOR" | "STAFF" | "ADMIN";

export type UserStatus = "PENDING_VERIFICATION" | "ACTIVE" | "LOCKED";

/** auth-service: AuthResponse. `refreshToken` hiện chưa dùng được — xem VD-05. */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
}

/** auth-service: UserResponse, trả về từ GET /auth/me. */
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
