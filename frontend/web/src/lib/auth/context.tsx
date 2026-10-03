"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useRouter } from "next/navigation";
import {
  SESSION_EXPIRED_EVENT,
  SESSION_REFRESHED_EVENT,
  authApi,
  getToken,
  readToken,
  refreshSession,
  setToken,
} from "@/lib/api";
import type { Role } from "@/types";

interface AuthState {
  email: string | null;
  userId: string | null;
  roles: Role[];
  /** false cho tới khi lần gọi /auth/refresh lúc khởi động (bootstrap) trả lời xong. */
  ready: boolean;
  signIn: (email: string, password: string) => Promise<Role[]>;
  signInWithGoogle: (idToken: string) => Promise<Role[]>;
  signInWithFacebook: (accessToken: string) => Promise<Role[]>;
  signOut: () => void;
  hasRole: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

/**
 * `token` và `ready` gộp chung MỘT state, không phải hai state riêng.
 *
 * Trước đây (thời còn localStorage) hai giá trị này lấy qua hai `useSyncExternalStore` riêng,
 * và từng có một lỗi: `ready` thành `true` sớm hơn `token` một nhịp render, khiến RouteGuard
 * thấy "sẵn sàng mà chưa đăng nhập" và đá người dùng về /login dù họ đang đăng nhập tử tế. Gộp
 * vào một object và set bằng một lệnh `setSession` duy nhất thì hai giá trị LUÔN đổi cùng một
 * lần render — không còn cách nào để lệch nhịp được nữa, kể cả nếu sau này React đổi cách gộp
 * (batch) các lần setState.
 */
interface Session {
  token: string | null;
  ready: boolean;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [session, setSession] = useState<Session>({ token: null, ready: false });

  // Bootstrap: access token nằm trong bộ nhớ JS nên mất sau khi tải lại trang — gọi /auth/refresh
  // một lần lúc khởi động để âm thầm khôi phục phiên từ refresh token (cookie httpOnly). Thất bại
  // (không có cookie, hoặc cookie đã hết hạn/bị thu hồi) thì coi như chưa đăng nhập, không phải lỗi.
  useEffect(() => {
    let cancelled = false;
    refreshSession().finally(() => {
      if (!cancelled) setSession({ token: getToken(), ready: true });
    });
    return () => {
      cancelled = true;
    };
  }, []);

  // Tầng API làm mới token ngầm xong (VD-05) thì đọc lại; tầng API thử làm mới mà vẫn không
  // được thì mới tới expired — đưa người dùng về trang đăng nhập, giữ lại đường dẫn đang xem.
  useEffect(() => {
    function onRefreshed() {
      setSession((prev) => ({ ...prev, token: getToken() }));
    }
    function onExpired() {
      setSession((prev) => ({ ...prev, token: null }));
      const here = window.location.pathname + window.location.search;
      router.replace(`/login?next=${encodeURIComponent(here)}&expired=1`);
    }
    window.addEventListener(SESSION_REFRESHED_EVENT, onRefreshed);
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => {
      window.removeEventListener(SESSION_REFRESHED_EVENT, onRefreshed);
      window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
    };
  }, [router]);

  const claims = useMemo(() => readToken(session.token), [session.token]);
  const roles = useMemo(() => claims?.roles ?? [], [claims]);

  const signIn = useCallback(async (email: string, password: string) => {
    const res = await authApi.login(email, password);
    setSession({ token: res.accessToken, ready: true });
    return readToken(res.accessToken)?.roles ?? [];
  }, []);

  const signInWithGoogle = useCallback(async (idToken: string) => {
    const res = await authApi.google(idToken);
    setSession({ token: res.accessToken, ready: true });
    return readToken(res.accessToken)?.roles ?? [];
  }, []);

  const signInWithFacebook = useCallback(async (accessToken: string) => {
    const res = await authApi.facebook(accessToken);
    setSession({ token: res.accessToken, ready: true });
    return readToken(res.accessToken)?.roles ?? [];
  }, []);

  const signOut = useCallback(() => {
    // authApi.logout xoá phiên ở máy ngay lập tức rồi mới báo server thu hồi refresh token,
    // nên giao diện đổi trạng thái liền, không chờ mạng.
    void authApi.logout();
    setToken(null);
    setSession((prev) => ({ ...prev, token: null }));
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
      ready: session.ready,
      signIn,
      signInWithGoogle,
      signInWithFacebook,
      signOut,
      hasRole,
    }),
    [claims, roles, session.ready, signIn, signInWithGoogle, signInWithFacebook, signOut, hasRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth phải nằm trong <AuthProvider>");
  return ctx;
}
