"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiError, productApi } from "@/lib/api";
import { Button, Dialog, ErrorState, SelectField, TextAreaField, TextField, useToast } from "@/components/ui";
import type { Category, Product, ProductRequest } from "@/types";

const schema = z.object({
  categoryId: z.string().min(1, "Chọn danh mục"),
  sku: z.string().min(1, "Nhập mã hàng").max(64),
  name: z.string().min(1, "Nhập tên sản phẩm").max(200),
  description: z.string().max(5000).optional(),
  price: z.coerce.number().min(0, "Giá không được âm"),
  unit: z.string().min(1, "Nhập đơn vị tính").max(30),
  imageUrl: z.string().max(500).optional(),
  initialStock: z.coerce.number().int().min(0).optional(),
  lowStockThreshold: z.coerce.number().int().min(0).optional(),
  active: z.boolean(),
});

// `z.coerce` nhận vào chuỗi từ <input type="number"> rồi trả ra số, nên input và output
// của schema khác kiểu nhau. Khai báo cả hai để useForm biết mình đang giữ gì và trả gì.
type FormInput = z.input<typeof schema>;
type FormValues = z.output<typeof schema>;

/**
 * Dùng chung cho tạo mới và sửa. Khi sửa, ô tồn kho ban đầu bị ẩn: backend bỏ qua trường
 * đó lúc cập nhật, và mọi thay đổi tồn phải đi qua phiếu nhập/điều chỉnh để có vết.
 */
export function ProductFormDialog({
  open,
  onClose,
  categories,
  editing,
}: {
  open: boolean;
  onClose: () => void;
  categories: Category[];
  editing: Product | null;
}) {
  const qc = useQueryClient();
  const toast = useToast();

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { active: true },
  });

  useEffect(() => {
    if (!open) return;
    reset(
      editing
        ? {
            categoryId: editing.categoryId,
            sku: editing.sku,
            name: editing.name,
            description: editing.description ?? "",
            price: editing.price,
            unit: editing.unit,
            imageUrl: editing.imageUrl ?? "",
            lowStockThreshold: editing.lowStockThreshold ?? undefined,
            active: editing.active,
          }
        : { active: true, categoryId: categories[0]?.id ?? "" },
    );
  }, [open, editing, categories, reset]);

  const save = useMutation({
    mutationFn: (values: FormValues) => {
      const body: ProductRequest = {
        ...values,
        description: values.description || undefined,
        imageUrl: values.imageUrl || undefined,
      };
      return editing ? productApi.update(editing.id, body) : productApi.create(body);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["products"] });
      toast.success(editing ? "Đã lưu sản phẩm" : "Đã thêm sản phẩm");
      onClose();
    },
    onError: (err) => {
      if (err instanceof ApiError && err.fieldErrors) {
        for (const [field, message] of Object.entries(err.fieldErrors)) {
          if (field in schema.shape) setError(field as keyof FormInput, { message });
        }
      }
    },
  });

  const generalError =
    save.error instanceof ApiError && !save.error.fieldErrors ? save.error.message : null;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={editing ? "Sửa sản phẩm" : "Thêm sản phẩm"}
      description={editing ? editing.sku : "Điền thông tin mặt hàng mới"}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Huỷ
          </Button>
          <Button
            form="product-form"
            type="submit"
            loading={save.isPending}
            disabled={categories.length === 0}
          >
            {editing ? "Lưu thay đổi" : "Thêm sản phẩm"}
          </Button>
        </>
      }
    >
      {categories.length === 0 ? (
        <p className="text-sm text-bark">
          Chưa có danh mục nào. Tạo danh mục trước rồi mới thêm được sản phẩm.
        </p>
      ) : (
        <form
          id="product-form"
          onSubmit={handleSubmit((v) => save.mutate(v))}
          className="space-y-4"
          noValidate
        >
          <TextField label="Tên sản phẩm" required error={errors.name?.message} {...register("name")} />

          <div className="grid gap-3 sm:grid-cols-2">
            <TextField
              label="Mã hàng"
              hint="Không trùng với mặt hàng khác"
              required
              error={errors.sku?.message}
              {...register("sku")}
            />
            <SelectField label="Danh mục" required error={errors.categoryId?.message} {...register("categoryId")}>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </SelectField>
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <TextField
              label="Giá bán"
              type="number"
              min={0}
              step={1000}
              required
              error={errors.price?.message}
              {...register("price")}
            />
            <TextField
              label="Đơn vị tính"
              hint="túi, hộp, lọ…"
              required
              error={errors.unit?.message}
              {...register("unit")}
            />
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            {!editing && (
              <TextField
                label="Tồn kho ban đầu"
                type="number"
                min={0}
                error={errors.initialStock?.message}
                {...register("initialStock")}
              />
            )}
            <TextField
              label="Ngưỡng cảnh báo"
              type="number"
              min={0}
              hint="Còn dưới mức này sẽ báo sắp hết"
              error={errors.lowStockThreshold?.message}
              {...register("lowStockThreshold")}
            />
          </div>

          <TextAreaField label="Mô tả" rows={3} error={errors.description?.message} {...register("description")} />

          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" className="size-4 accent-[var(--moss)]" {...register("active")} />
            Đang bán
          </label>

          {editing && (
            <p className="text-xs text-bark">
              Đổi tồn kho phải qua phiếu nhập hoặc điều chỉnh, không sửa ở đây — như vậy mới có
              vết trong lịch sử.
            </p>
          )}

          {generalError && <ErrorState message={generalError} />}
        </form>
      )}
    </Dialog>
  );
}
