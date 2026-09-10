import type { ApiErrorBody } from "@/types";

/**
 * Trình duyệt gọi thẳng gateway sẽ dính CORS, nên mọi request đi qua /api/* của chính Next
 * rồi được rewrite sang API Gateway (next.config.ts) — cùng origin, không cần cấu hình CORS.
 * Đổi đích bằng API_GATEWAY_URL, không hard-code địa chỉ service ở bất kỳ đâu.
 */
const BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api";

const TOKEN_KEY = "vetclinic.accessToken";

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string | null) {
  if (typeof window === "undefined") return;
  try {
    if (token) window.localStorage.setItem(TOKEN_KEY, token);
    else window.localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Trình duyệt chặn storage (cửa sổ riêng tư) — coi như chưa đăng nhập.
  }
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly fieldErrors?: Record<string, string>,
    readonly body?: unknown,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/**
 * Phiên hết hạn được phát ra một lần cho toàn ứng dụng thay vì để mỗi màn hình tự xử lý.
 * auth-service không có endpoint refresh (VD-05) nên không làm silent refresh được —
 * cách duy nhất là đưa người dùng về trang đăng nhập.
 */
export const SESSION_EXPIRED_EVENT = "vetclinic:session-expired";

function announceSessionExpired() {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT));
}

interface RequestOptions extends RequestInit {
  /** Endpoint công khai: không đính token, và 401 không coi là hết phiên. */
  publicRoute?: boolean;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { publicRoute = false, ...init } = options;

  const headers = new Headers(init.headers);
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (!publicRoute) {
    const token = getToken();
    if (token) headers.set("Authorization", `Bearer ${token}`);
  }

  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, { ...init, headers });
  } catch {
    throw new ApiError(0, "Không kết nối được máy chủ. Kiểm tra hệ thống đã chạy chưa.");
  }

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  const body: unknown = text ? safeParse(text) : null;

  if (!res.ok) {
    if (res.status === 401 && !publicRoute && getToken()) {
      setToken(null);
      announceSessionExpired();
    }
    const err = (body ?? {}) as Partial<ApiErrorBody>;
    throw new ApiError(res.status, err.message || fallbackMessage(res.status), err.fieldErrors, body);
  }

  return body as T;
}

function fallbackMessage(status: number): string {
  switch (status) {
    case 401:
      return "Phiên đăng nhập đã hết hạn. Đăng nhập lại để tiếp tục.";
    case 403:
      return "Tài khoản của bạn không có quyền thực hiện thao tác này.";
    case 404:
      return "Không tìm thấy dữ liệu.";
    case 409:
      return "Thao tác không hợp lệ ở trạng thái hiện tại.";
    // Gateway trả 503 khoảng 30 giây đầu sau khi khởi động lại, trước khi Eureka
    // kịp đẩy registry xuống (VD-13) — nói rõ để người dùng chờ chứ không tưởng là hỏng.
    case 503:
      return "Dịch vụ đang khởi động. Thử lại sau vài giây.";
    default:
      return `Yêu cầu thất bại (HTTP ${status}).`;
  }
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

const json = (body: unknown) => (body === undefined ? undefined : JSON.stringify(body));

export const http = {
  get: <T>(path: string, publicRoute = false) => request<T>(path, { method: "GET", publicRoute }),
  post: <T>(path: string, body?: unknown, publicRoute = false) =>
    request<T>(path, { method: "POST", body: json(body), publicRoute }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: "PUT", body: json(body) }),
  del: <T>(path: string) => request<T>(path, { method: "DELETE" }),
};

/** Ghép query string, bỏ qua giá trị rỗng để không gửi tham số thừa. */
export function qs(params: Record<string, string | number | boolean | undefined | null>): string {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === "") continue;
    sp.set(k, String(v));
  }
  const s = sp.toString();
  return s ? `?${s}` : "";
}
