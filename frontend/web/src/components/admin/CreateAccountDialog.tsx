"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiError, authApi } from "@/lib/api";
import { Button, Dialog, ErrorState, SelectField, TextField, useToast } from "@/components/ui";

// Khớp CreateStaffAccountRequest ở auth-service: tên ≤ 100 ký tự, mật khẩu 8–100, chỉ DOCTOR/STAFF.
const schema = z.object({
  familyName: z.string().trim().min(1, "Nhập họ").max(100),
  givenName: z.string().trim().min(1, "Nhập tên").max(100),
  email: z.email("Email không hợp lệ"),
  password: z.string().min(8, "Mật khẩu tối thiểu 8 ký tự").max(100),
  role: z.enum(["DOCTOR", "STAFF"]),
});

type FormValues = z.infer<typeof schema>;

/**
 * Tạo tài khoản bác sĩ hoặc nhân viên. Tài khoản hoạt động ngay, không cần xác minh email;
 * người được cấp nên đổi mật khẩu sau lần đăng nhập đầu (chưa có chức năng đổi — VD-08).
 *
 * Thứ tự ô theo cách gọi tên Việt Nam: "Họ" đi vào firstName, "Tên" vào lastName — cùng quy ước
 * với sự kiện user.staff-created ghép họ tên cho profile-service (VD-20).
 */
export function CreateAccountDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { role: "DOCTOR" } });

  useEffect(() => {
    if (open) reset({ role: "DOCTOR", familyName: "", givenName: "", email: "", password: "" });
  }, [open, reset]);

  const create = useMutation({
    mutationFn: (v: FormValues) =>
      authApi.createStaffAccount({ firstName: v.familyName, lastName: v.givenName, email: v.email, password: v.password, role: v.role }),
    onSuccess: (_, v) => {
      qc.invalidateQueries({ queryKey: ["admin-users"] });
      qc.invalidateQueries({ queryKey: ["doctors"] });
      toast.success(`Đã tạo tài khoản ${v.email}`);
      onClose();
    },
    onError: (err) => {
      if (err instanceof ApiError && err.status === 409) {
        setError("email", { message: "Email này đã có tài khoản" });
      }
    },
  });

  const generalError =
    create.error instanceof ApiError && create.error.status !== 409
      ? create.error.fieldErrors
        ? "Thông tin chưa hợp lệ, kiểm tra lại các ô."
        : create.error.message
      : create.error && !(create.error instanceof ApiError)
        ? "Không tạo được tài khoản."
        : null;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Tạo tài khoản"
      description="Cho bác sĩ hoặc nhân viên quầy. Khách hàng tự đăng ký trên website."
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Huỷ
          </Button>
          <Button form="create-account-form" type="submit" loading={create.isPending}>
            Tạo tài khoản
          </Button>
        </>
      }
    >
      <form id="create-account-form" onSubmit={handleSubmit((v) => create.mutate(v))} className="space-y-4" noValidate>
        <SelectField label="Vai trò" required {...register("role")}>
          <option value="DOCTOR">Bác sĩ</option>
          <option value="STAFF">Nhân viên</option>
        </SelectField>
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField label="Họ" required autoComplete="off" error={errors.familyName?.message} {...register("familyName")} />
          <TextField label="Tên" required autoComplete="off" error={errors.givenName?.message} {...register("givenName")} />
        </div>
        <TextField label="Email đăng nhập" type="email" required autoComplete="off" error={errors.email?.message} {...register("email")} />
        <TextField
          label="Mật khẩu ban đầu"
          type="password"
          required
          autoComplete="new-password"
          hint="Tối thiểu 8 ký tự. Gửi cho người được cấp qua kênh riêng."
          error={errors.password?.message}
          {...register("password")}
        />
        {generalError && <ErrorState message={generalError} />}
      </form>
    </Dialog>
  );
}
