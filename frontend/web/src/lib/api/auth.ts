import { http, setToken } from "./client";
import type {
  AuthResponse,
  CreateStaffAccountRequest,
  CurrentUser,
  RegisterRequest,
  Role,
  TokenClaims,
} from "@/types";

export const authApi = {
  async login(email: string, password: string): Promise<AuthResponse> {
    const res = await http.post<AuthResponse>("/auth/login", { email, password }, true);
    setToken(res.accessToken);
    return res;
  },

  register: (body: RegisterRequest) =>
    http.post<{ message: string }>("/auth/register", body, true),

  verifyEmail: (token: string) =>
    http.get<{ message: string }>(`/auth/verify-email?token=${encodeURIComponent(token)}`, true),

  me: () => http.get<CurrentUser>("/auth/me"),

  /** Chỉ ADMIN. Backend chỉ cho tạo tài khoản DOCTOR và STAFF. */
  createStaffAccount: (body: CreateStaffAccountRequest) =>
    http.post<CurrentUser>("/admin/users", body),

  deleteUser: (userId: string) => http.del<void>(`/admin/users/${userId}`),
};

/**
 * Đọc payload JWT để hiển thị và ẩn/hiện menu. Không dùng cho phân quyền thật —
 * backend mới là nơi quyết định, frontend chỉ lo trải nghiệm.
 */
export function readToken(token: string | null): TokenClaims | null {
  if (!token) return null;
  const part = token.split(".")[1];
  if (!part) return null;
  try {
    const raw = atob(part.replace(/-/g, "+").replace(/_/g, "/"));
    // Payload là UTF-8; atob trả chuỗi byte nên phải giải lại kẻo hỏng tiếng Việt.
    const decoded = decodeURIComponent(
      Array.from(raw, (c) => `%${c.charCodeAt(0).toString(16).padStart(2, "0")}`).join(""),
    );
    return JSON.parse(decoded) as TokenClaims;
  } catch {
    return null;
  }
}

export function isExpired(claims: TokenClaims | null): boolean {
  if (!claims?.exp) return false;
  return claims.exp * 1000 <= Date.now();
}

export const ROLE_LABEL: Record<Role, string> = {
  ADMIN: "Quản trị",
  DOCTOR: "Bác sĩ",
  STAFF: "Nhân viên",
  CUSTOMER: "Khách hàng",
};
