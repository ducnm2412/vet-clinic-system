"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { homeFor, isNextAllowedForRoles } from "@/config/nav";

interface GoogleCredentialResponse {
  credential: string;
}

interface GoogleAccountsId {
  initialize: (config: {
    client_id: string;
    callback: (response: GoogleCredentialResponse) => void;
  }) => void;
  renderButton: (
    parent: HTMLElement,
    options: { theme: string; size: string; shape: string; text: string },
  ) => void;
}

declare global {
  interface Window {
    google?: { accounts: { id: GoogleAccountsId } };
  }
}

const SCRIPT_SRC = "https://accounts.google.com/gsi/client";
let scriptPromise: Promise<void> | null = null;

function loadGoogleScript(): Promise<void> {
  if (window.google?.accounts?.id) return Promise.resolve();
  if (scriptPromise) return scriptPromise;

  scriptPromise = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = SCRIPT_SRC;
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Không tải được Google Sign-In."));
    document.head.appendChild(script);
  });
  return scriptPromise;
}

/**
 * Nút icon tròn "Đăng nhập bằng Google" — renderButton kiểu "icon" chính chủ của Google
 * đang lỗi (SVG bên trong co về 0px, đã kiểm chứng trực tiếp), nên dùng nút "standard" —
 * loại luôn render đúng — rồi đặt trong khung tròn 40px overflow-hidden, chỉ để lộ đúng góc
 * icon (khớp vì bán kính bo của cạnh trái nút pill cũng là 20px, bằng bán kính khung). Vẫn
 * là nút thật của Google, chỉ cắt khung hiển thị — không giả lập, click vẫn rơi đúng chỗ.
 * Tự ẩn nếu chưa cấu hình NEXT_PUBLIC_GOOGLE_CLIENT_ID (xem .env.example).
 */
export function GoogleSignInButton() {
  const { signInWithGoogle } = useAuth();
  const router = useRouter();
  const params = useSearchParams();
  const next = params.get("next");
  const containerRef = useRef<HTMLDivElement>(null);
  const [error, setError] = useState<string | null>(null);

  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;

  useEffect(() => {
    if (!clientId || !containerRef.current) return;
    let cancelled = false;

    loadGoogleScript()
      .then(() => {
        if (cancelled || !window.google || !containerRef.current) return;

        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: async (response) => {
            setError(null);
            try {
              const roles = await signInWithGoogle(response.credential);
              const target = next && isNextAllowedForRoles(next, roles) ? next : homeFor(roles);
              router.replace(target);
            } catch {
              setError("Không đăng nhập được bằng Google. Thử lại sau.");
            }
          },
        });
        window.google.accounts.id.renderButton(containerRef.current, {
          theme: "outline",
          size: "large",
          shape: "pill",
          text: "signin_with",
        });
      })
      .catch(() => setError("Không tải được Google Sign-In."));

    return () => {
      cancelled = true;
    };
  }, [clientId, next, router, signInWithGoogle]);

  if (!clientId) return null;

  return (
    <div className="flex flex-col items-center">
      <div className="relative size-10 overflow-hidden rounded-full">
        <div ref={containerRef} className="absolute left-0 top-0" />
      </div>
      {error && <p className="mt-2 text-center text-[12px] text-coral-deep">{error}</p>}
    </div>
  );
}
