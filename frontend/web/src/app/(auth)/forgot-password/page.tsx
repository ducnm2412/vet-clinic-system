"use client";

import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { authApi } from "@/lib/api";
import { SiteButton } from "@/components/site/primitives";
import { SiteInput } from "@/components/site/fields";

const schema = z.object({
  email: z.string().min(1, "Nhập email").email("Email không đúng định dạng"),
});

type FormValues = z.infer<typeof schema>;

export default function ForgotPasswordPage() {
  const [formError, setFormError] = useState<string | null>(null);
  const [sentTo, setSentTo] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    setFormError(null);
    try {
      await authApi.forgotPassword(values.email);
      setSentTo(values.email);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Không gửi được yêu cầu. Thử lại sau nhé.");
    }
  }

  if (sentTo) {
    return (
      <>
        <h1 className="t-h2">Kiểm tra hộp thư</h1>
        {/* Cố tình nói "nếu" — backend trả cùng một câu dù email có tài khoản hay không, giao diện
            không được tiết lộ thêm. */}
        <p className="mt-4 text-stone">
          Nếu <strong className="text-pine">{sentTo}</strong> đã đăng ký, chúng tôi vừa gửi hướng dẫn
          đặt lại mật khẩu tới đó. Link có hiệu lực trong 1 giờ.
        </p>
        <p className="mt-4 text-[15px] text-stone">
          Không thấy thư? Kiểm tra mục thư rác, hoặc chờ khoảng một phút rồi{" "}
          <button
            type="button"
            onClick={() => setSentTo(null)}
            className="text-teal-deep underline underline-offset-4"
          >
            thử lại
          </button>
          .
        </p>
        {process.env.NODE_ENV !== "production" && (
          <p className="mt-4 text-[15px] text-stone">
            Đang chạy môi trường phát triển — nếu cấu hình dùng hòm thư giả thì thư nằm ở{" "}
            <a
              href={process.env.NEXT_PUBLIC_MAILHOG_URL ?? "http://127.0.0.1:8025"}
              target="_blank"
              rel="noreferrer"
              className="text-teal-deep underline underline-offset-4"
            >
              MailHog
            </a>
            .
          </p>
        )}
        <Link href="/login" className="mt-8 inline-block text-teal-deep underline underline-offset-4">
          Về trang đăng nhập
        </Link>
      </>
    );
  }

  return (
    <>
      <h1 className="t-h2">Quên mật khẩu</h1>
      <p className="mt-3 text-stone">
        Nhập email đã đăng ký, chúng tôi sẽ gửi link để bạn đặt mật khẩu mới.
      </p>

      <form onSubmit={handleSubmit(onSubmit)} className="mt-8 space-y-5" noValidate>
        <SiteInput
          label="Email"
          type="email"
          autoComplete="email"
          required
          error={errors.email?.message}
          {...register("email")}
        />

        {formError && (
          <p role="alert" className="rounded-[var(--radius-card)] bg-peach px-4 py-3 text-[15px] text-coral-deep">
            {formError}
          </p>
        )}

        <SiteButton type="submit" size="lg" loading={isSubmitting} className="w-full">
          Gửi link đặt lại
        </SiteButton>
      </form>

      <Link href="/login" className="mt-8 inline-block text-teal-deep underline underline-offset-4">
        Quay lại đăng nhập
      </Link>
    </>
  );
}
