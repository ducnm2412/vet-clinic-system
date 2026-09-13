import type { ApiErrorBody } from "@/types";

/**
 * Trình duyệt gọi thẳng gateway sẽ dính CORS, nên mọi request đi qua /api/* của chính Next
 * rồi được rewrite sang API Gateway (next.config.ts) — cùng origin, không cần cấu hình CORS.
 * Đổi đích bằng API_GATEWAY_URL, không hard-code địa chỉ service ở bất kỳ đâu.
 */
const BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api";

const TOKEN_KEY = "vetclinic.accessToken";
const REFRESH_KEY = "vetclinic.refreshToken";

function readStorage(key: string): string | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStorage(key: string, value: string | null) {
  if (typeof window === "undefined") return;
  try {
    if (value) window.localStorage.setItem(key, value);
    else window.localStorage.removeItem(key);
  } catch {
    // Trình duyệt chặn storage (cửa sổ riêng tư) — coi như chưa đăng nhập.
  }
}

export function getToken(): string | null {
  return readStorage(TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return readStorage(REFRESH_KEY);
}

/** Gọi với null là xoá cả hai token — access token không đứng một mình được. */
export function setToken(token: string | null) {
  writeStorage(TOKEN_KEY, token);
  if (!token) writeStorage(REFRESH_KEY, null);
}

export function setSession(accessToken: string, refreshToken: string) {
  writeStorage(TOKEN_KEY, accessToken);
  writeStorage(REFRESH_KEY, refreshToken);
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
 * Chỉ phát khi đã thử làm mới bằng refresh token mà vẫn không được.
 */
export const SESSION_EXPIRED_EVENT = "vetclinic:session-expired";

/** Phát sau khi làm mới ngầm thành công, để AuthProvider đọc lại token mới. */
export const SESSION_REFRESHED_EVENT = "vetclinic:session-refreshed";

function announceSessionExpired() {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT));
}

interface RequestOptions extends RequestInit {
  /** Endpoint công khai: không đính token, và 401 không coi là hết phiên. */
  publicRoute?: boolean;
}

/*
  VD-05 — làm mới phiên ngầm.

  Access token sống 15 phút. Gặp 401 thì đổi refresh token lấy cặp mới rồi gửi lại đúng
  request đó một lần, người dùng không thấy gì.

  Chỉ có MỘT lần làm mới chạy tại một thời điểm. Một trang thường bắn ba bốn request cùng
  lúc; nếu mỗi cái tự làm mới thì cái thứ hai sẽ dùng refresh token mà cái thứ nhất vừa
  xoay vòng mất — backend coi đó là token bị đánh cắp và thu hồi SẠCH mọi phiên. Nên mọi
  request gặp 401 cùng chờ chung một lời hứa.
*/
let refreshing: Promise<boolean> | null = null;

function refreshSession(): Promise<boolean> {
  if (refreshing) return refreshing;

  refreshing = (async () => {
    const refreshToken = getRefreshToken();
    if (!refreshToken) return false;
    try {
      const res = await fetch(`${BASE}/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });
      if (!res.ok) return false;
      const body = (await res.json()) as { accessToken: string; refreshToken: string };
      setSession(body.accessToken, body.refreshToken);
      if (typeof window !== "undefined") {
        window.dispatchEvent(new CustomEvent(SESSION_REFRESHED_EVENT));
      }
      return true;
    } catch {
      return false;
    }
  })().finally(() => {
    refreshing = null;
  });

  return refreshing;
}

export async function request<T>(
  path: string,
  options: RequestOptions = {},
  retried = false,
): Promise<T> {
  const { publicRoute = false, ...init } = options;

  const headers = new Headers(init.headers);
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  // Nhớ token đã gửi đi, để lúc gặp 401 còn biết nó có còn là token mới nhất hay không.
  const sentToken = publicRoute ? null : getToken();
  if (sentToken) headers.set("Authorization", `Bearer ${sentToken}`);

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
      // Request này có thể đã gửi bằng token cũ nhưng về muộn, sau khi một request khác làm
      // mới xong rồi. Khi đó trong máy đã có token mới — chỉ cần gửi lại, KHÔNG làm mới thêm.
      // Không có bước này thì một trang ba request sẽ xoay vòng refresh token ba lần.
      // `retried` chặn vòng lặp: token mới mà vẫn 401 thì là không có quyền thật.
      if (!retried) {
        const tokenChanged = getToken() !== sentToken;
        if (tokenChanged || (await refreshSession())) {
          return request<T>(path, options, true);
        }
      }
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
