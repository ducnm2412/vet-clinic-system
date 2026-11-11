"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { homeFor, isNextAllowedForRoles } from "@/config/nav";
import {
  DEMO_ACCOUNTS,
  DEMO_PASSWORD,
  MOCK_AUTH_ENABLED,
} from "@/lib/api/mock-auth"; // MOCK-TẠM
import { SiteButton } from "@/components/site/primitives";
import { SiteInput } from "@/components/site/fields";
import { SocialSignInButtons } from "@/components/site/SocialSignInButtons";

const schema = z.object({
  email: z.string().min(1, "Nhập email").email("Email không đúng định dạng"),
  password: z.string().min(1, "Nhập mật khẩu"),
});

type FormValues = z.infer<typeof schema>;

function LoginForm() {
  const { signIn } = useAuth();
  const router = useRouter();
  const params = useSearchParams();
  const next = params.get("next");
  const expired = params.get("expired") === "1";

  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    setFormError(null);
    try {
      const roles = await signIn(values.email, values.password);
      // Quay lại đúng chỗ đang dở nếu hợp với vai trò, không thì về trang chính của vai trò
      // (hoặc trang chấm công nếu bác sĩ/nhân viên chưa chấm công hôm nay) — xem landingFor.
      router.replace(await landingFor(roles, next));
    } catch (err) {
      // Backend cố tình trả cùng một lỗi cho sai mật khẩu, không tồn tại và chưa kích hoạt —
      // không đoán thêm để không lộ tài khoản nào có thật. Riêng tài khoản bị khoá trả 403, và
      // chỉ trả khi mật khẩu đã đúng — người đoán mò không biết được email nào đang bị khoá.
      setFormError(
        err instanceof ApiError && err.status === 401
          ? "Email hoặc mật khẩu không đúng, hoặc tài khoản chưa xác minh email."
          : err instanceof ApiError && err.status === 403
            ? "Tài khoản này đã bị khoá. Liên hệ phòng khám để được hỗ trợ."
            : err instanceof Error
              ? err.message
              : "Không đăng nhập được.",
      );
    }
  }

  return (
    <>
      <h1 className="t-h2">Đăng nhập</h1>
      <p className="mt-3 text-stone">
        Chưa có tài khoản?{" "}
        <Link
          href="/register"
          className="text-teal-deep underline underline-offset-4"
        >
          Đăng ký
        </Link>
      </p>

      {expired && (
        <p className="mt-6 rounded-[var(--radius-card)] bg-peach px-4 py-3 text-[15px]">
          Phiên làm việc đã hết hạn. Đăng nhập lại để tiếp tục.
        </p>
      )}

      <form
        onSubmit={handleSubmit(onSubmit)}
        className="mt-8 space-y-5"
        noValidate
      >
        <SiteInput
          label="Email"
          type="email"
          autoComplete="email"
          required
          error={errors.email?.message}
          {...register("email")}
        />
        <SiteInput
          label="Mật khẩu"
          type="password"
          autoComplete="current-password"
          required
          error={errors.password?.message}
          {...register("password")}
        />
        <div className="-mt-2 text-right">
          <Link
            href="/forgot-password"
            className="text-[15px] text-teal-deep underline underline-offset-4"
          >
            Quên mật khẩu?
          </Link>
        </div>

        {formError && (
          <p
            role="alert"
            className="rounded-[var(--radius-card)] bg-peach px-4 py-3 text-[15px] text-coral-deep"
          >
            {formError}
          </p>
        )}

        <SiteButton
          type="submit"
          size="lg"
          loading={isSubmitting}
          className="w-full"
        >
          Đăng nhập
        </SiteButton>
      </form>

      <SocialSignInButtons />

      {/* MOCK-TẠM: đăng nhập nhanh bằng tài khoản demo, chỉ hiện khi chạy dev. */}
      {MOCK_AUTH_ENABLED && (
        <div className="mt-6 rounded-2xl border border-dashed border-[#f3c9a1] bg-[#fff8f1] p-4">
          <p className="text-sm font-semibold text-pine">
            Tài khoản demo (không cần backend)
          </p>
          <p className="mt-1 text-xs text-stone">
            Mật khẩu chung: {DEMO_PASSWORD}
          </p>
          <div className="mt-3 grid grid-cols-2 gap-2">
            {DEMO_ACCOUNTS.map((a) => (
              <button
                key={a.email}
                type="button"
                disabled={isSubmitting}
                onClick={() =>
                  onSubmit({ email: a.email, password: DEMO_PASSWORD })
                }
                className="rounded-xl bg-white px-3 py-2 text-left text-sm shadow-sm transition hover:bg-[#fff1e3]"
              >
                <span className="block font-semibold text-pine">{a.label}</span>
                <span className="block truncate text-xs text-stone">
                  {a.email}
                </span>
              </button>
            ))}
          </div>
        </div>
      )}
    </>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginForm />
    </Suspense>
  );
}
