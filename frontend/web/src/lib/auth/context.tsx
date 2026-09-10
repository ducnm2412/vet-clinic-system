"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useSyncExternalStore,
  type ReactNode,
} from "react";
import { useRouter } from "next/navigation";
import { SESSION_EXPIRED_EVENT, authApi, getToken, isExpired, readToken, setToken } from "@/lib/api";
import type { Role } from "@/types";

interface AuthState {
  email: string | null;
  userId: string | null;
  roles: Role[];
  /** false trong lần render đầu vì token nằm ở localStorage, chỉ client mới đọc được. */
  ready: boolean;
  signIn: (email: string, password: string) => Promise<Role[]>;
  signOut: () => void;
  hasRole: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

/**
 * Token nằm trong localStorage — một kho dữ liệu bên ngoài React. Dùng
 * `useSyncExternalStore` thay vì đọc trong effect rồi setState: cách này không gây thêm
 * một vòng render, và mọi tab/thành phần cùng đọc một nguồn.
 */
const listeners = new Set<() => void>();

function notify() {
  for (const l of listeners) l();
}

function subscribe(onChange: () => void) {
  listeners.add(onChange);
  // Đăng xuất ở tab khác cũng phải phản ánh sang tab này.
  window.addEventListener("storage", onChange);
  return () => {
    listeners.delete(onChange);
    window.removeEventListener("storage", onChange);
  };
}

function getSnapshot(): string | null {
  const stored = getToken();
  // Token hết hạn coi như không có, khỏi để giao diện tưởng còn đăng nhập rồi mọi request 401.
  if (stored && isExpired(readToken(stored))) {
    setToken(null);
    return null;
  }
  return stored;
}

/** Máy chủ không có localStorage; luôn coi là chưa đăng nhập để HTML hai bên khớp nhau. */
function getServerSnapshot(): string | null {
  return null;
}

/*
  `ready` phải đi cùng nhịp với `token`, và cũng phải lấy qua useSyncExternalStore.

  Trước đây chỗ này là `typeof window !== "undefined"`, tức là ngay lần commit đầu tiên
  trên trình duyệt nó đã true — nhưng lần commit đó React vẫn đang dùng ảnh chụp phía máy
  chủ, nên `token` còn null. Effect của RouteGuard chạy đúng vào lúc đó, thấy "sẵn sàng mà
  chưa đăng nhập", và đá người dùng về trang đăng nhập dù họ đang đăng nhập tử tế. Lỗi chỉ
  lộ ra khi mở thẳng một địa chỉ cần quyền, không lộ khi bấm chuyển trang trong ứng dụng.

  Lấy chung một nguồn thì hai giá trị đổi cùng một lần render: commit đầu là (false, null)
  nên RouteGuard đứng yên, commit sau là (true, token thật) nên nó xử đúng.
*/
function getReady(): boolean {
  return true;
}

function getServerReady(): boolean {
  return false;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const token = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
  const ready = useSyncExternalStore(subscribe, getReady, getServerReady);

  // auth-service không có endpoint làm mới token (VD-05) nên không gia hạn ngầm được.
  // Bất kỳ 401 nào cũng đưa người dùng về trang đăng nhập, giữ lại đường dẫn đang xem.
  useEffect(() => {
    function onExpired() {
      notify();
      const here = window.location.pathname + window.location.search;
      router.replace(`/login?next=${encodeURIComponent(here)}&expired=1`);
    }
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, [router]);

  const claims = useMemo(() => readToken(token), [token]);
  const roles = useMemo(() => claims?.roles ?? [], [claims]);

  const signIn = useCallback(async (email: string, password: string) => {
    const res = await authApi.login(email, password);
    notify();
    return readToken(res.accessToken)?.roles ?? [];
  }, []);

  const signOut = useCallback(() => {
    setToken(null);
    notify();
  }, []);

  const hasRole = useCallback(
    (...wanted: Role[]) => wanted.some((r) => roles.includes(r)),
    [roles],
  );

  const value = useMemo<AuthState>(
    () => ({
      email: claims?.sub ?? null,
      userId: claims?.userId ?? null,
      roles,
      ready,
      signIn,
      signOut,
      hasRole,
    }),
    [claims, roles, ready, signIn, signOut, hasRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth phải nằm trong <AuthProvider>");
  return ctx;
}
