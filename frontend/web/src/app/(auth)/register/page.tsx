"use client";

import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ApiError, authApi } from "@/lib/api";
import { Button, ErrorState, TextField } from "@/components/ui";

const schema = z
  .object({
    firstName: z.string().min(1, "Nhập họ").max(100),
    lastName: z.string().min(1, "Nhập tên").max(100),
    email: z.string().min(1, "Nhập email").email("Email không đúng định dạng"),
    password: z.string().min(8, "Mật khẩu cần ít nhất 8 ký tự").max(100),
    confirmPassword: z.string().min(1, "Nhập lại mật khẩu"),
  })
  .refine((v) => v.password === v.confirmPassword, {
    path: ["confirmPassword"],
    message: "Hai mật khẩu không khớp",
  });

type FormValues = z.infer<typeof schema>;

export default function RegisterPage() {
  const [formError, setFormError] = useState<string | null>(null);
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    setFormError(null);
    try {
      await authApi.register(values);
      setRegisteredEmail(values.email);
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) {
        // Lỗi theo trường của backend đưa thẳng về đúng ô, không dồn vào một dòng chung.
        for (const [field, message] of Object.entries(err.fieldErrors)) {
          if (field in schema.shape) {
            setError(field as keyof FormValues, { message });
          }
        }
      } else {
        setFormError(err instanceof Error ? err.message : "Không tạo được tài khoản.");
      }
    }
  }

  if (registeredEmail) {
    return (
      <>
        <h1 className="font-[family-name:var(--font-display)] text-[25px] text-ink">
          Kiểm tra hộp thư
        </h1>
        <p className="mt-3 text-sm leading-relaxed text-ink-soft">
          Đã gửi liên kết xác minh tới <strong className="text-ink">{registeredEmail}</strong>. Mở
          liên kết đó rồi quay lại đăng nhập.
        </p>
        {/* Chỉ nhắc hòm thư giả khi chạy dev — người dùng thật không cần biết MailHog là gì. */}
        {process.env.NODE_ENV !== "production" && (
          <p className="mt-3 text-sm leading-relaxed text-bark">
            Đang chạy môi trường phát triển nên thư không ra Internet mà vào hòm thư giả{" "}
            <a
              href={process.env.NEXT_PUBLIC_MAILHOG_URL ?? "http://127.0.0.1:8025"}
              target="_blank"
              rel="noreferrer"
              className="text-moss underline underline-offset-2"
            >
              MailHog
            </a>
            .
          </p>
        )}
        <Link href="/login" className="mt-6 inline-block text-sm text-moss underline underline-offset-2">
          Tới trang đăng nhập
        </Link>
      </>
    );
  }

  return (
    <>
      <h1 className="font-[family-name:var(--font-display)] text-[25px] text-ink">Tạo tài khoản</h1>
      <p className="mt-1 text-sm text-bark">
        Đã có tài khoản?{" "}
        <Link href="/login" className="text-moss underline underline-offset-2">
          Đăng nhập
        </Link>
      </p>

      <form onSubmit={handleSubmit(onSubmit)} className="mt-6 space-y-4" noValidate>
        <div className="grid grid-cols-2 gap-3">
          <TextField label="Họ" required error={errors.firstName?.message} {...register("firstName")} />
          <TextField label="Tên" required error={errors.lastName?.message} {...register("lastName")} />
        </div>
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          required
          error={errors.email?.message}
          {...register("email")}
        />
        <TextField
          label="Mật khẩu"
          type="password"
          autoComplete="new-password"
          hint="Ít nhất 8 ký tự"
          required
          error={errors.password?.message}
          {...register("password")}
        />
        <TextField
          label="Nhập lại mật khẩu"
          type="password"
          autoComplete="new-password"
          required
          error={errors.confirmPassword?.message}
          {...register("confirmPassword")}
        />

        {formError && <ErrorState message={formError} />}

        <Button type="submit" size="lg" loading={isSubmitting} className="w-full">
          Tạo tài khoản
        </Button>
      </form>
    </>
  );
}
