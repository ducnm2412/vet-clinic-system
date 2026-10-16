"use client";

import { GoogleSignInButton } from "@/components/site/GoogleSignInButton";

/**
 * Dải đăng nhập mạng xã hội dùng chung cho trang đăng nhập/đăng ký — một divider "hoặc" rồi
 * nút icon Google (tự ẩn khi thiếu client ID).
 *
 * Nút Facebook (FacebookSignInButton) đang TẠM ẨN: FB.login() bị Facebook chặn trên trang http
 * (kể cả localhost) nên cần origin https mới chạy được. Muốn bật lại: import lại
 * FacebookSignInButton, đặt cạnh GoogleSignInButton, và đặt FACEBOOK_APP_ID/SECRET trong .env.
 */
export function SocialSignInButtons() {
  return (
    <div className="mt-6">
      <div className="relative mb-5 text-center text-[13px] text-stone">
        <span className="absolute inset-y-1/2 left-0 h-px w-[42%] bg-mist" aria-hidden />
        hoặc
        <span className="absolute inset-y-1/2 right-0 h-px w-[42%] bg-mist" aria-hidden />
      </div>
      <div className="flex justify-center gap-4">
        <GoogleSignInButton />
      </div>
    </div>
  );
}
