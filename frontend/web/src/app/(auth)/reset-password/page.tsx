"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { CheckCircle2, XCircle } from "lucide-react";
import { ApiError, authApi } from "@/lib/api";
import { ButtonLink, SiteButton } from "@/components/site/primitives";
import { SiteInput } from "@/components/site/fields";

const schema = z
  .object({
    newPassword: z.string().min(8, "Mật khẩu cần ít nhất 8 ký tự").max(100),
    confirmPassword: z.string().min(1, "Nhập lại mật khẩu"),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    path: ["confirmPassword"],
    message: "Hai mật khẩu không khớp",
  });

type FormValues = z.infer<typeof schema>;

/**
 * Đích đến của link trong email đặt lại mật khẩu (`/reset-password?token=...`). Token nằm ở
 * query string nên chỉ đọc ở client và gửi nguyên trong body POST — không bao giờ đặt vào URL
 * của request API.
 */
function ResetPasswordForm() {
  const token = useSearchParams().get("token");

  const [formError, setFormError] = useState<string | null>(null);
  const [linkInvalid, setLinkInvalid] = useState(false);
  const [done, setDone] = useState(false);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    setFormError(null);
    try {
      await authApi.resetPassword({ token: token!, ...values });
      setDone(true);
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) {
        for (const [field, message] of Object.entries(err.fieldErrors)) {
          if (field in schema.shape) setError(field as keyof FormValues, { message });
        }
      } else if (err instanceof ApiError && err.status === 400) {
        // Link sai, hết hạn hoặc đã dùng rồi — backend trả chung một lỗi; nhập lại mật khẩu cũng
        // vô ích nên chuyển sang màn "xin link mới" thay vì để người dùng thử đi thử lại.
        setLinkInvalid(true);
      } else {
        setFormError(err instanceof Error ? err.message : "Không đặt lại được mật khẩu.");
      }
    }
  }

  if (!token || linkInvalid) {
    return (
      <Result
        ok={false}
        title="Link không dùng được"
        message="Link đặt lại mật khẩu không hợp lệ, đã hết hạn hoặc đã được dùng. Bạn có thể xin link mới."
      >
        <ButtonLink href="/forgot-password" size="lg" className="mt-8">
          Xin link mới
        </ButtonLink>
      </Result>
    );
  }

  if (done) {
    return (
      <Result
        ok
        title="Đã đổi mật khẩu"
        message="Mật khẩu mới đã có hiệu lực. Các thiết bị đang đăng nhập đã bị đăng xuất, hãy đăng nhập lại."
      >
        <ButtonLink href="/login" size="lg" className="mt-8">
          Tới trang đăng nhập
        </ButtonLink>
      </Result>
    );
  }

  return (
    <>
      <h1 className="t-h2">Đặt mật khẩu mới</h1>
      <p className="mt-3 text-stone">Chọn mật khẩu mới cho tài khoản của bạn.</p>

      <form onSubmit={handleSubmit(onSubmit)} className="mt-8 space-y-5" noValidate>
        <SiteInput
          label="Mật khẩu mới"
          type="password"
          autoComplete="new-password"
          hint="Ít nhất 8 ký tự"
          required
          error={errors.newPassword?.message}
          {...register("newPassword")}
        />
        <SiteInput
          label="Nhập lại mật khẩu"
          type="password"
          autoComplete="new-password"
          required
          error={errors.confirmPassword?.message}
          {...register("confirmPassword")}
        />

        {formError && (
          <p role="alert" className="rounded-[var(--radius-card)] bg-peach px-4 py-3 text-[15px] text-coral-deep">
            {formError}
          </p>
        )}

        <SiteButton type="submit" size="lg" loading={isSubmitting} className="w-full">
          Đổi mật khẩu
        </SiteButton>
      </form>

      <Link href="/login" className="mt-8 inline-block text-teal-deep underline underline-offset-4">
        Quay lại đăng nhập
      </Link>
    </>
  );
}

function Result({
  ok,
  title,
  message,
  children,
}: {
  ok: boolean;
  title: string;
  message: string;
  children?: React.ReactNode;
}) {
  const Icon = ok ? CheckCircle2 : XCircle;
  return (
    <div>
      <Icon aria-hidden className={ok ? "size-9 text-teal" : "size-9 text-coral-deep"} />
      <h1 className="t-h2 mt-5">{title}</h1>
      <p className="mt-4 text-stone">{message}</p>
      {children}
    </div>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={null}>
      <ResetPasswordForm />
    </Suspense>
  );
}
