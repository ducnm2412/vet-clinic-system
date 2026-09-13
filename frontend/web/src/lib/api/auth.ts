import { getRefreshToken, http, qs, setSession, setToken } from "./client";
import type {
  AuthResponse,
  PageResponse,
  UserFilters,
  CreateStaffAccountRequest,
  CurrentUser,
  RegisterRequest,
  Role,
  TokenClaims,
} from "@/types";

export const authApi = {
  async login(email: string, password: string): Promise<AuthResponse> {
    const res = await http.post<AuthResponse>("/auth/login", { email, password }, true);
    setSession(res.accessToken, res.refreshToken);
    return res;
  },

  /**
   * Thu hồi refresh token ở server rồi xoá phiên ở máy. Xoá ở máy luôn chạy, kể cả khi mất
   * mạng — người bấm đăng xuất phải thoát ra được, không phải chờ server trả lời.
   */
  async logout(): Promise<void> {
    const refreshToken = getRefreshToken();
    setToken(null);
    if (!refreshToken) return;
    try {
      await http.post<void>("/auth/logout", { refreshToken }, true);
    } catch {
      // Server không nhận được thì token vẫn tự hết hạn sau 7 ngày; ở máy đã xoá rồi.
    }
  },

  register: (body: RegisterRequest) =>
    http.post<{ message: string }>("/auth/register", body, true),

  verifyEmail: (token: string) =>
    http.get<{ message: string }>(`/auth/verify-email?token=${encodeURIComponent(token)}`, true),

  me: () => http.get<CurrentUser>("/auth/me"),

  /** Chỉ ADMIN. Backend chỉ cho tạo tài khoản DOCTOR và STAFF. */
  createStaffAccount: (body: CreateStaffAccountRequest) =>
    http.post<{ message: string }>("/admin/users", body),

  /** Chỉ ADMIN. Nguồn duy nhất có họ tên, email, vai trò và trạng thái của mọi tài khoản. */
  listUsers: (filters: UserFilters = {}) =>
    http.get<PageResponse<CurrentUser>>(`/admin/users${qs({ ...filters })}`),

  /**
   * CN-08: khoá thay cho xoá — lịch sử khám và đơn hàng giữ nguyên. Backend thu hồi mọi phiên và
   * từ chối (409) khi tự khoá mình hoặc khoá admin cuối cùng. Giao diện không có nút xoá.
   */
  lockUser: (userId: string) => http.put<CurrentUser>(`/admin/users/${userId}/lock`),

  unlockUser: (userId: string) => http.put<CurrentUser>(`/admin/users/${userId}/unlock`),
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
