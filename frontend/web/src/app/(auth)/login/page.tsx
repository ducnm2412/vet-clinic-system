"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { homeFor } from "@/config/nav";
import { SiteButton } from "@/components/site/primitives";
import { SiteInput } from "@/components/site/fields";

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
      // Quay lại đúng chỗ đang dở, nếu không thì về trang chính của vai trò.
      router.replace(next || homeFor(roles));
    } catch (err) {
      // Backend cố tình trả cùng một lỗi cho sai mật khẩu, không tồn tại và chưa kích hoạt —
      // không đoán thêm để không lộ tài khoản nào có thật.
      setFormError(
        err instanceof ApiError && err.status === 401
          ? "Email hoặc mật khẩu không đúng, hoặc tài khoản chưa xác minh email."
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
        <Link href="/register" className="text-teal-deep underline underline-offset-4">
          Đăng ký
        </Link>
      </p>

      {expired && (
        <p className="mt-6 rounded-[var(--radius-card)] bg-peach px-4 py-3 text-[15px]">
          Phiên làm việc đã hết hạn. Đăng nhập lại để tiếp tục.
        </p>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="mt-8 space-y-5" noValidate>
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

        {formError && (
          <p role="alert" className="rounded-[var(--radius-card)] bg-peach px-4 py-3 text-[15px] text-coral-deep">
            {formError}
          </p>
        )}

        <SiteButton type="submit" size="lg" loading={isSubmitting} className="w-full">
          Đăng nhập
        </SiteButton>
      </form>
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
