"use client";

import { GoogleSignInButton } from "@/components/site/GoogleSignInButton";
import { FacebookSignInButton } from "@/components/site/FacebookSignInButton";

/**
 * Dải đăng nhập mạng xã hội dùng chung cho trang đăng nhập/đăng ký — một divider "hoặc" rồi
 * hai nút icon Google/Facebook xếp cạnh nhau. FacebookSignInButton cố tình luôn hiện (App ID
 * sẽ bổ sung sau) nên dải này luôn hiện; chỉ GoogleSignInButton tự ẩn khi thiếu client ID.
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
        <FacebookSignInButton />
      </div>
    </div>
  );
}
