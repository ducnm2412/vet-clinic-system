"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { useQuery } from "@tanstack/react-query";
import { CheckCircle2, XCircle } from "lucide-react";
import { ApiError, authApi } from "@/lib/api";
import { Button, Spinner } from "@/components/ui";

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

  if (verify.isPending) return <Spinner label="Đang xác minh tài khoản" />;

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
      <Icon aria-hidden className={`size-7 ${ok ? "text-moss" : "text-danger"}`} />
      <h1 className="mt-4 font-[family-name:var(--font-display)] text-[25px] text-ink">{title}</h1>
      <p className="mt-2 text-sm leading-relaxed text-bark">{message}</p>
      <Link href="/login" className="mt-6 inline-block">
        <Button size="lg">Tới trang đăng nhập</Button>
      </Link>
    </div>
  );
}

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={<Spinner label="Đang mở trang xác minh" />}>
      <VerifyEmail />
    </Suspense>
  );
}
