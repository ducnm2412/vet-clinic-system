"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { landingFor } from "@/lib/landing";

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
    options: {
      theme: string;
      size: string;
      shape: string;
      text: string;
      width?: number;
      locale?: string;
      logo_alignment?: string;
    },
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
 * Nút "Đăng nhập bằng Google" chính chủ, kiểu "standard", rộng bằng khung chứa (Google giới
 * hạn 400px). Không cắt khung tròn nữa: khi trình duyệt đã đăng nhập Google, Google tự đổi
 * sang nút cá nhân hoá (avatar + "Tiếp tục với tên …"), cắt khung sẽ chỉ lộ avatar và chữ vỡ.
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
              router.replace(await landingFor(roles, next));
            } catch {
              setError("Không đăng nhập được bằng Google. Thử lại sau.");
            }
          },
        });
        const width = Math.min(400, Math.max(200, containerRef.current.offsetWidth));
        window.google.accounts.id.renderButton(containerRef.current, {
          theme: "outline",
          size: "large",
          shape: "rectangular",
          text: "signin_with",
          width,
          locale: "vi",
          logo_alignment: "center",
        });
      })
      .catch(() => setError("Không tải được Google Sign-In."));

    return () => {
      cancelled = true;
    };
  }, [clientId, next, router, signInWithGoogle]);

  if (!clientId) return null;

  return (
    <div className="flex w-full flex-col items-center">
      <div ref={containerRef} className="flex min-h-11 w-full justify-center" />
      {error && <p className="mt-2 text-center text-[12px] text-coral-deep">{error}</p>}
    </div>
  );
}
