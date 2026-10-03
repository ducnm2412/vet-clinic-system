"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { homeFor, isNextAllowedForRoles } from "@/config/nav";

interface FacebookAuthResponse {
  accessToken: string;
}

interface FacebookLoginResponse {
  authResponse: FacebookAuthResponse | null;
}

interface FacebookSdk {
  init: (config: { appId: string; cookie: boolean; xfbml: boolean; version: string }) => void;
  login: (
    callback: (response: FacebookLoginResponse) => void,
    options: { scope: string },
  ) => void;
}

declare global {
  interface Window {
    FB?: FacebookSdk;
    fbAsyncInit?: () => void;
  }
}

const SDK_SRC = "https://connect.facebook.net/vi_VN/sdk.js";
const SDK_VERSION = "v21.0";
let sdkPromise: Promise<FacebookSdk> | null = null;

function loadFacebookSdk(appId: string): Promise<FacebookSdk> {
  if (window.FB) return Promise.resolve(window.FB);
  if (sdkPromise) return sdkPromise;

  sdkPromise = new Promise((resolve, reject) => {
    window.fbAsyncInit = () => {
      if (!window.FB) {
        reject(new Error("Facebook SDK không khởi tạo được."));
        return;
      }
      window.FB.init({ appId, cookie: true, xfbml: false, version: SDK_VERSION });
      resolve(window.FB);
    };

    const script = document.createElement("script");
    script.src = SDK_SRC;
    script.async = true;
    script.defer = true;
    script.crossOrigin = "anonymous";
    script.onerror = () => reject(new Error("Không tải được Facebook SDK."));
    document.head.appendChild(script);
  });
  return sdkPromise;
}

/**
 * Nút icon tròn "Đăng nhập bằng Facebook" — cùng vai trò với GoogleSignInButton, xếp cạnh nhau
 * trong SocialSignInButtons. Facebook không có sẵn nút icon dựng thẳng như Google nên tự vẽ
 * (nền xanh thương hiệu + logo "f"), bấm vào mới gọi FB.login(). Cố tình LUÔN hiện — kể cả khi
 * chưa cấu hình NEXT_PUBLIC_FACEBOOK_APP_ID (App ID/Secret sẽ bổ sung sau) — bấm vào lúc đó chỉ
 * báo "chưa sẵn sàng" thay vì im lặng không phản hồi.
 */
export function FacebookSignInButton() {
  const { signInWithFacebook } = useAuth();
  const router = useRouter();
  const params = useSearchParams();
  const next = params.get("next");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const fbRef = useRef<FacebookSdk | null>(null);

  const appId = process.env.NEXT_PUBLIC_FACEBOOK_APP_ID;

  useEffect(() => {
    if (!appId) return;
    let cancelled = false;

    loadFacebookSdk(appId)
      .then((fb) => {
        if (!cancelled) fbRef.current = fb;
      })
      .catch(() => setError("Không tải được Facebook SDK."));

    return () => {
      cancelled = true;
    };
  }, [appId]);

  function handleClick() {
    if (!appId) {
      setError("Đăng nhập Facebook chưa được cấu hình.");
      return;
    }
    if (!fbRef.current) return;
    setError(null);
    setLoading(true);

    fbRef.current.login(async (response) => {
      if (!response.authResponse) {
        // Người dùng đóng hộp thoại hoặc bấm huỷ — không phải lỗi, im lặng thôi.
        setLoading(false);
        return;
      }
      try {
        const roles = await signInWithFacebook(response.authResponse.accessToken);
        const target = next && isNextAllowedForRoles(next, roles) ? next : homeFor(roles);
        router.replace(target);
      } catch {
        setError("Không đăng nhập được bằng Facebook. Thử lại sau.");
        setLoading(false);
      }
    }, { scope: "email" });
  }

  return (
    <div className="flex flex-col items-center">
      <button
        type="button"
        aria-label="Đăng nhập bằng Facebook"
        onClick={handleClick}
        disabled={loading}
        className="flex size-10 items-center justify-center rounded-full bg-[#1877F2] text-white transition-[filter,transform] duration-200 [transition-timing-function:var(--ease)] hover:brightness-110 active:translate-y-px disabled:cursor-not-allowed disabled:opacity-60"
      >
        {loading ? (
          <span
            aria-hidden
            className="size-4 animate-spin rounded-full border-2 border-white border-t-transparent"
          />
        ) : (
          <svg viewBox="0 0 24 24" aria-hidden className="size-5" fill="currentColor">
            <path d="M22 12.06C22 6.51 17.52 2 12 2S2 6.51 2 12.06c0 5 3.66 9.15 8.44 9.94v-7.03H7.9v-2.91h2.54V9.86c0-2.5 1.49-3.89 3.77-3.89 1.09 0 2.24.2 2.24.2v2.46h-1.26c-1.24 0-1.63.77-1.63 1.56v1.87h2.78l-.44 2.91h-2.34V22c4.78-.79 8.44-4.94 8.44-9.94Z" />
          </svg>
        )}
      </button>
      {error && <p className="mt-2 text-center text-[12px] text-coral-deep">{error}</p>}
    </div>
  );
}
