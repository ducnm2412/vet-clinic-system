"use client";

import { useEffect } from "react";
import { useMutation } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ApiError, authApi } from "@/lib/api";
import { Button, Dialog, ErrorState, TextField, useToast } from "@/components/ui";

const schema = z
  .object({
    currentPassword: z.string().min(1, "Nhập mật khẩu hiện tại"),
    newPassword: z.string().min(8, "Mật khẩu cần ít nhất 8 ký tự").max(100),
    confirmPassword: z.string().min(1, "Nhập lại mật khẩu mới"),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    path: ["confirmPassword"],
    message: "Hai mật khẩu không khớp",
  });

type FormValues = z.infer<typeof schema>;
const EMPTY: FormValues = { currentPassword: "", newPassword: "", confirmPassword: "" };

/**
 * Đổi mật khẩu cho tài khoản đang đăng nhập ở khu vực quản trị (admin, bác sĩ, nhân viên) — mở từ
 * thẻ người dùng ở thanh bên. Người được cấp tài khoản mới nên đổi mật khẩu ban đầu ở đây.
 */
export function ChangePasswordDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const toast = useToast();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: EMPTY });

  useEffect(() => {
    if (open) reset(EMPTY);
  }, [open, reset]);

  const change = useMutation({
    mutationFn: (v: FormValues) => authApi.changePassword(v),
    onSuccess: () => {
      toast.success("Đã đổi mật khẩu. Các thiết bị khác sẽ phải đăng nhập lại.");
      onClose();
    },
    onError: (err) => {
      if (err instanceof ApiError && err.fieldErrors) {
        for (const [field, message] of Object.entries(err.fieldErrors)) {
          if (field in EMPTY) setError(field as keyof FormValues, { message });
        }
      }
    },
  });

  // Lỗi không thuộc ô nào (vd tài khoản đăng nhập bằng Google chưa có mật khẩu, mất mạng).
  const generalError =
    change.error && !(change.error instanceof ApiError && change.error.fieldErrors)
      ? change.error instanceof Error
        ? change.error.message
        : "Không đổi được mật khẩu."
      : null;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Đổi mật khẩu"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Huỷ
          </Button>
          <Button form="change-password-form" type="submit" loading={change.isPending}>
            Cập nhật mật khẩu
          </Button>
        </>
      }
    >
      <form id="change-password-form" onSubmit={handleSubmit((v) => change.mutate(v))} className="space-y-4" noValidate>
        <TextField
          label="Mật khẩu hiện tại"
          type="password"
          required
          autoComplete="current-password"
          error={errors.currentPassword?.message}
          {...register("currentPassword")}
        />
        <TextField
          label="Mật khẩu mới"
          type="password"
          required
          autoComplete="new-password"
          hint="Tối thiểu 8 ký tự, khác mật khẩu hiện tại."
          error={errors.newPassword?.message}
          {...register("newPassword")}
        />
        <TextField
          label="Xác nhận mật khẩu mới"
          type="password"
          required
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register("confirmPassword")}
        />
        {generalError && <ErrorState message={generalError} />}
      </form>
    </Dialog>
  );
}
