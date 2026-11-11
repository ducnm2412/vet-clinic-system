// MOCK-TẠM: tài khoản demo đăng nhập KHÔNG cần backend, để chỉnh giao diện các khu vực theo vai trò.
// Chỉ bật khi chạy dev (NODE_ENV !== "production"). XOÁ file này cùng các dòng "MOCK-TẠM" khi xong.
import type { Role } from "@/types";

export const MOCK_AUTH_ENABLED = process.env.NODE_ENV !== "production";
export const DEMO_PASSWORD = "demo1234";

export const DEMO_ACCOUNTS: Array<{ email: string; roles: Role[]; label: string }> = [
  { email: "admin@demo.vn", roles: ["ADMIN"], label: "Quản trị" },
  { email: "doctor@demo.vn", roles: ["DOCTOR"], label: "Bác sĩ" },
  { email: "staff@demo.vn", roles: ["STAFF"], label: "Nhân viên" },
  { email: "customer@demo.vn", roles: ["CUSTOMER"], label: "Khách hàng" },
];

const STORAGE_KEY = "vetclinic:mock-token";

function b64url(text: string): string {
  const bytes = new TextEncoder().encode(text);
  let bin = "";
  bytes.forEach((b) => (bin += String.fromCharCode(b)));
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** Token giả có cùng dạng JWT để readToken() đọc được sub / userId / roles. Chữ ký vô nghĩa. */
export function mockLogin(email: string, password: string): string | null {
  if (!MOCK_AUTH_ENABLED || password !== DEMO_PASSWORD) return null;
  const acc = DEMO_ACCOUNTS.find((a) => a.email === email.trim().toLowerCase());
  if (!acc) return null;
  const payload = {
    sub: acc.email,
    userId: `demo-${acc.roles[0].toLowerCase()}`,
    roles: acc.roles,
    exp: Math.floor(Date.now() / 1000) + 7 * 24 * 3600,
  };
  const token = `${b64url(JSON.stringify({ alg: "none", typ: "JWT" }))}.${b64url(JSON.stringify(payload))}.demo`;
  try {
    sessionStorage.setItem(STORAGE_KEY, token);
  } catch {}
  return token;
}

/** Khôi phục phiên demo sau khi tải lại trang (thay cho /auth/refresh). */
export function restoreMockToken(): string | null {
  if (!MOCK_AUTH_ENABLED || typeof window === "undefined") return null;
  try {
    return sessionStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export function clearMockToken() {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {}
}
