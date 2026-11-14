"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiError, medicationApi } from "@/lib/api";
import { Button, Dialog, TextAreaField, TextField, useToast } from "@/components/ui";
import type { Medication } from "@/types";

// Giới hạn khớp cột DB và MedicationRequest của backend: vượt thì chặn ngay ở đây thay vì nhận 400.
const schema = z.object({
  name: z.string().trim().min(1, "Nhập tên thuốc").max(255, "Tối đa 255 ký tự"),
  unit: z.string().trim().max(50, "Tối đa 50 ký tự").optional(),
  caution: z.string().max(2000, "Tối đa 2000 ký tự").optional(),
  // Giữ dạng chuỗi để phân biệt "để trống" (chưa có giá) với 0; đổi sang số lúc gửi.
  unitPrice: z
    .string()
    .trim()
    .optional()
    .refine((v) => !v || (Number.isFinite(Number(v)) && Number(v) >= 0), "Đơn giá phải là số không âm"),
});

type FormValues = z.infer<typeof schema>;

/**
 * Thêm hoặc sửa một thuốc trong danh mục. Ẩn/hiện thuốc là nút riêng ở bảng, không nằm đây —
 * nên khi sửa không gửi `active`, backend giữ nguyên trạng thái hiện tại.
 */
export function MedicationFormDialog({
  open,
  onClose,
  editing,
}: {
  open: boolean;
  onClose: () => void;
  /** Có thì là sửa, không có thì là thêm mới. */
  editing: Medication | null;
}) {
  const qc = useQueryClient();
  const toast = useToast();

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: editing?.name ?? "",
      unit: editing?.unit ?? "",
      caution: editing?.caution ?? "",
      unitPrice: editing?.unitPrice != null ? String(editing.unitPrice) : "",
    },
  });

  const save = useMutation({
    mutationFn: (v: FormValues) => {
      // Ô trống gửi là undefined: backend lưu null, "chưa có lưu ý" khác "lưu ý rỗng".
      const body = {
        name: v.name,
        unit: v.unit || undefined,
        caution: v.caution || undefined,
        unitPrice: v.unitPrice ? Number(v.unitPrice) : undefined,
      };
      return editing ? medicationApi.update(editing.id, body) : medicationApi.create(body);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["medications"] });
      toast.success(editing ? "Đã lưu thuốc" : "Đã thêm thuốc vào danh mục");
      onClose();
    },
    onError: (err) => {
      // Trùng tên (không phân biệt hoa/thường và dấu): chỉ đúng ô đang sai thay vì báo chung chung.
      if (err instanceof ApiError && err.status === 409) {
        setError("name", { message: err.message });
        return;
      }
      toast.error(err instanceof ApiError ? err.message : "Không lưu được thuốc.");
    },
  });

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={editing ? `Sửa ${editing.name}` : "Thêm thuốc"}
      description="Bác sĩ gõ tên thuốc khi kê đơn sẽ được gợi ý từ danh mục này. Sửa ở đây không làm đổi các đơn đã kê."
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Huỷ
          </Button>
          <Button form="medication-form" type="submit" loading={save.isPending}>
            {editing ? "Lưu thay đổi" : "Thêm thuốc"}
          </Button>
        </>
      }
    >
      <form
        id="medication-form"
        onSubmit={handleSubmit((v) => save.mutate(v))}
        noValidate
        className="space-y-4"
      >
        <TextField label="Tên thuốc" required error={errors.name?.message} {...register("name")} />

        <TextField
          label="Đơn vị"
          placeholder="viên, gói, ml…"
          error={errors.unit?.message}
          {...register("unit")}
        />

        <TextField
          label="Đơn giá (VND / đơn vị)"
          type="number"
          min={0}
          step={500}
          inputMode="numeric"
          placeholder="VD: 12000"
          hint="Phiếu thu tiền thuốc tự điền giá này; để trống thì quầy nhập tay. Đổi giá không làm đổi phiếu đã tạo."
          error={errors.unitPrice?.message}
          {...register("unitPrice")}
        />

        <TextAreaField
          label="Lưu ý"
          rows={3}
          placeholder="VD: Không dùng cho mèo, thận trọng khi chó mang thai"
          hint="Hiện cho bác sĩ ngay khi chọn thuốc này lúc kê đơn."
          error={errors.caution?.message}
          {...register("caution")}
        />
      </form>
    </Dialog>
  );
}
