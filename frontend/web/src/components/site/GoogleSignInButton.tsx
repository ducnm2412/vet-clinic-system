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

/** Chữ "G" nhiều màu của Google, đúng theo bộ nhận diện của Google Sign-In. */
function GoogleLogo({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 48 48" aria-hidden className={className}>
      <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
      <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
      <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
      <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
    </svg>
  );
}

/** Google giới hạn nút của họ tối đa 400px — vẽ ở cỡ này rồi kéo giãn phủ kín nút của mình. */
const GSI_WIDTH = 400;

/**
 * Nút "Đăng nhập bằng Google" cùng cỡ với nút "Đăng nhập" (rộng hết khung, cao 56px, bo 12px).
 *
 * Google không cho nút chính chủ rộng quá 400px, nên phần NHÌN THẤY là nút tự vẽ (logo G + chữ),
 * còn nút thật của Google (renderButton) được đặt trong suốt, kéo giãn phủ kín lên trên. Người dùng
 * bấm vào đâu cũng là bấm nút thật của Google — luồng đăng nhập, popup và credential vẫn do Google
 * xử lý, không giả lập. Rê chuột/focus vào nút thật vẫn làm nút hiển thị đổi màu (group-hover,
 * focus-within).
 * Tự ẩn nếu chưa cấu hình NEXT_PUBLIC_GOOGLE_CLIENT_ID (xem .env.example).
 */
export function GoogleSignInButton() {
  const { signInWithGoogle } = useAuth();
  const router = useRouter();
  const params = useSearchParams();
  const next = params.get("next");
  const wrapRef = useRef<HTMLDivElement>(null);
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
        window.google.accounts.id.renderButton(containerRef.current, {
          theme: "outline",
          size: "large",
          shape: "rectangular",
          text: "signin_with",
          width: GSI_WIDTH,
          locale: "vi",
        });
      })
      .catch(() => setError("Không tải được Google Sign-In."));

    return () => {
      cancelled = true;
    };
  }, [clientId, next, router, signInWithGoogle]);

  // Kéo giãn nút thật của Google cho phủ kín nút hiển thị, kể cả khi khung đổi cỡ (xoay điện thoại…).
  useEffect(() => {
    const wrap = wrapRef.current;
    const inner = containerRef.current;
    if (!wrap || !inner || typeof ResizeObserver === "undefined") return;
    const fit = () => {
      const w = inner.offsetWidth || GSI_WIDTH;
      const h = inner.offsetHeight || 44;
      inner.style.transform = `scale(${wrap.clientWidth / w}, ${wrap.clientHeight / h})`;
    };
    const observer = new ResizeObserver(fit);
    observer.observe(wrap);
    observer.observe(inner);
    return () => observer.disconnect();
  }, [clientId]);

  if (!clientId) return null;

  return (
    <div className="w-full">
      <div className="group relative h-14 w-full">
        <div
          aria-hidden
          className="pointer-events-none flex h-full w-full items-center justify-center gap-3 rounded-xl border border-[#ead9c5] bg-white text-[17px] font-medium text-[#1f2a24] transition-colors group-hover:bg-[#fff4ea] group-focus-within:ring-2 group-focus-within:ring-[#f26b1d]/40"
        >
          <GoogleLogo className="size-5" />
          Đăng nhập bằng Google
        </div>
        <div ref={wrapRef} className="absolute inset-0 overflow-hidden rounded-xl opacity-0">
          <div ref={containerRef} className="w-fit origin-top-left" />
        </div>
      </div>
      {error && <p className="mt-2 text-center text-[12px] text-coral-deep">{error}</p>}
    </div>
  );
}
