"use client";


import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { useQuery } from "@tanstack/react-query";
import { CheckCircle2, XCircle } from "lucide-react";
import { ApiError, authApi } from "@/lib/api";
import { ButtonLink } from "@/components/site/primitives";

/**
 * Đích đến của link trong email xác minh. notification-service dựng link bằng
 * FRONTEND_URL nên nó trỏ về đây (`/verify-email?token=...`), không phải về gateway —
 * trang này nhận token rồi gọi `GET /auth/verify-email` giúp người dùng.
 */
function VerifyEmail() {
  const token = useSearchParams().get("token");

  const verify = useQuery({
    queryKey: ["verify-email", token],
    queryFn: () => authApi.verifyEmail(token!),
    enabled: !!token,
    retry: false,
  });

  if (!token) {
    return (
      <Result
        ok={false}
        title="Thiếu mã xác minh"
        message="Đường dẫn không có mã xác minh. Mở lại đúng link trong email nhé."
      />
    );
  }

  if (verify.isPending) return <Waiting label="Đang xác minh tài khoản" />;

  if (verify.isError) {
    // Token dùng rồi, hết hạn, hoặc sai — backend trả cùng một lỗi cho cả ba.
    return (
      <Result
        ok={false}
        title="Không xác minh được"
        message={
          verify.error instanceof ApiError
            ? verify.error.message
            : "Mã xác minh không hợp lệ hoặc đã hết hạn."
        }
      />
    );
  }

  return (
    <Result
      ok
      title="Tài khoản đã kích hoạt"
      message="Giờ bạn đăng nhập được rồi."
    />
  );
}

function Result({ ok, title, message }: { ok: boolean; title: string; message: string }) {
  const Icon = ok ? CheckCircle2 : XCircle;
  return (
    <div>
      <Icon aria-hidden className={ok ? "size-9 text-teal" : "size-9 text-coral-deep"} />
      <h1 className="t-h2 mt-5">{title}</h1>
      <p className="mt-4 text-stone">{message}</p>
      <ButtonLink href="/login" size="lg" className="mt-8">
        Tới trang đăng nhập
      </ButtonLink>
    </div>
  );
}

/** Vòng quay chờ, cùng màu thương hiệu với phần còn lại của mặt tiền. */
function Waiting({ label }: { label: string }) {
  return (
    <div role="status" className="flex items-center gap-3 text-stone">
      <span
        aria-hidden
        className="size-5 animate-spin rounded-full border-2 border-teal border-t-transparent"
      />
      {label}
    </div>
  );
}

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={<Waiting label="Đang mở trang xác minh" />}>
      <VerifyEmail />
    </Suspense>
  );
}
