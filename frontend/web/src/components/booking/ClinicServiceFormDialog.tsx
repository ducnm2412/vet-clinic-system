"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiError, clinicServiceApi } from "@/lib/api";
import { Button, Dialog, TextAreaField, TextField, useToast } from "@/components/ui";
import type { ClinicService } from "@/types";

const schema = z.object({
  // Backend chỉ nhận chữ thường, số và gạch ngang — chặn ngay ở đây để khỏi phải đi một vòng.
  slug: z
    .string()
    .min(1, "Nhập mã dịch vụ")
    .max(80)
    .regex(/^[a-z0-9-]+$/, "Chỉ gồm chữ thường không dấu, số và dấu gạch ngang"),
  name: z.string().min(1, "Nhập tên dịch vụ").max(120),
  description: z.string().max(2000).optional(),
  durationMinutes: z.coerce.number().int().min(5, "Ít nhất 5 phút").max(480, "Nhiều nhất 8 tiếng"),
  referencePrice: z.coerce.number().min(0, "Giá không được âm").optional(),
  displayOrder: z.coerce.number().int().min(0).optional(),
});

type FormInput = z.input<typeof schema>;
type FormValues = z.output<typeof schema>;

/** VD-21. Dùng chung cho thêm mới và sửa — ngừng cung cấp là nút riêng ở bảng, không nằm đây. */
export function ClinicServiceFormDialog({
  open,
  onClose,
  editing,
}: {
  open: boolean;
  onClose: () => void;
  /** Có thì là sửa, không có thì là thêm mới. */
  editing: ClinicService | null;
}) {
  const qc = useQueryClient();
  const toast = useToast();

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      slug: editing?.slug ?? "",
      name: editing?.name ?? "",
      description: editing?.description ?? "",
      durationMinutes: editing?.durationMinutes ?? 30,
      referencePrice: editing?.referencePrice ?? undefined,
      displayOrder: editing?.displayOrder ?? 0,
    },
  });

  const save = useMutation({
    mutationFn: (v: FormValues) => {
      const body = {
        slug: v.slug,
        name: v.name,
        description: v.description || undefined,
        durationMinutes: v.durationMinutes,
        referencePrice: v.referencePrice,
        displayOrder: v.displayOrder,
      };
      return editing ? clinicServiceApi.update(editing.id, body) : clinicServiceApi.create(body);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["clinic-services"] });
      toast.success(editing ? "Đã lưu dịch vụ" : "Đã thêm dịch vụ");
      onClose();
    },
    onError: (err) => {
      // Trùng mã dịch vụ: chỉ ra đúng ô đang sai thay vì một thông báo chung chung.
      if (err instanceof ApiError && err.status === 409) {
        setError("slug", { message: err.message });
        return;
      }
      toast.error(err instanceof ApiError ? err.message : "Không lưu được dịch vụ.");
    },
  });

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={editing ? `Sửa ${editing.name}` : "Thêm dịch vụ"}
      description="Danh sách này hiện trên trang chủ và trong bước chọn dịch vụ lúc khách đặt lịch."
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Huỷ
          </Button>
          <Button form="clinic-service-form" type="submit" loading={save.isPending}>
            {editing ? "Lưu thay đổi" : "Thêm dịch vụ"}
          </Button>
        </>
      }
    >
      <form
        id="clinic-service-form"
        onSubmit={handleSubmit((v) => save.mutate(v))}
        noValidate
        className="space-y-4"
      >
        <TextField label="Tên dịch vụ" required error={errors.name?.message} {...register("name")} />

        <TextField
          label="Mã dịch vụ"
          required
          placeholder="tiem-phong"
          hint="Không dấu, dùng gạch ngang. Giao diện chọn biểu tượng theo mã này."
          error={errors.slug?.message}
          {...register("slug")}
        />

        <TextAreaField
          label="Mô tả"
          rows={3}
          hint="Đoạn này hiện nguyên văn trên trang chủ."
          error={errors.description?.message}
          {...register("description")}
        />

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField
            label="Thời lượng (phút)"
            type="number"
            min="5"
            max="480"
            error={errors.durationMinutes?.message}
            {...register("durationMinutes")}
          />
          <TextField
            label="Giá tham khảo"
            type="number"
            min="0"
            step="1000"
            hint="Để trống nếu phải khám xong mới báo giá."
            error={errors.referencePrice?.message}
            {...register("referencePrice")}
          />
          <TextField
            label="Thứ tự hiện"
            type="number"
            min="0"
            error={errors.displayOrder?.message}
            {...register("displayOrder")}
          />
        </div>
      </form>
    </Dialog>
  );
}
