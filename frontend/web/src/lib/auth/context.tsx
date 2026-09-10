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
import { SESSION_EXPIRED_EVENT, authApi, getToken, isExpired, readToken, setToken } from "@/lib/api";
import type { Role } from "@/types";

interface AuthState {
  email: string | null;
  userId: string | null;
  roles: Role[];
  /** false cho tới khi đọc xong token từ localStorage — tránh nhấp nháy nội dung sai. */
  ready: boolean;
  signIn: (email: string, password: string) => Promise<Role[]>;
  signOut: () => void;
  hasRole: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(null);
  const [ready, setReady] = useState(false);
  const router = useRouter();

  useEffect(() => {
    const stored = getToken();
    // Token hết hạn thì dọn luôn, khỏi để giao diện tưởng còn đăng nhập rồi mọi request 401.
    if (stored && isExpired(readToken(stored))) {
      setToken(null);
      setTokenState(null);
    } else {
      setTokenState(stored);
    }
    setReady(true);
  }, []);

  // auth-service không có endpoint refresh (VD-05) nên không gia hạn ngầm được.
  // Bất kỳ 401 nào cũng đưa người dùng về trang đăng nhập, giữ lại đường dẫn đang xem.
  useEffect(() => {
    function onExpired() {
      setTokenState(null);
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
    setTokenState(res.accessToken);
    return readToken(res.accessToken)?.roles ?? [];
  }, []);

  const signOut = useCallback(() => {
    setToken(null);
    setTokenState(null);
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
