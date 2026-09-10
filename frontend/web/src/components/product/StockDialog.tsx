"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiError, productApi } from "@/lib/api";
import { Button, Dialog, ErrorState, SelectField, TextAreaField, TextField, useToast } from "@/components/ui";
import type { Product } from "@/types";

const schema = z
  .object({
    type: z.enum(["IMPORT", "ADJUSTMENT"]),
    quantityChange: z.coerce.number().int(),
    note: z.string().max(500).optional(),
  })
  .refine((v) => v.quantityChange !== 0, {
    path: ["quantityChange"],
    message: "Số lượng phải khác 0",
  })
  .refine((v) => v.type !== "IMPORT" || v.quantityChange > 0, {
    path: ["quantityChange"],
    message: "Nhập kho thì số lượng phải dương",
  });

// z.coerce nhan chuoi tu <input type="number"> roi tra ra so, nen input va output khac kieu.
type FormInput = z.input<typeof schema>;
type FormValues = z.output<typeof schema>;

/**
 * Nhập kho và điều chỉnh sau kiểm kê. Đây là con đường duy nhất đổi tồn từ giao diện —
 * mỗi lần đều ghi một dòng vào lịch sử biến động kèm số tồn sau đó.
 */
export function StockDialog({
  product,
  onClose,
}: {
  product: Product | null;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const toast = useToast();

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { type: "IMPORT" },
  });

  useEffect(() => {
    if (product) reset({ type: "IMPORT", quantityChange: 0, note: "" });
  }, [product, reset]);

  const type = watch("type");
  const change = Number(watch("quantityChange") || 0);
  const after = product ? product.stockQuantity + change : 0;

  const submit = useMutation({
    mutationFn: (values: FormValues) => {
      if (!product) throw new Error("Chưa chọn sản phẩm");
      return productApi.adjustStock(product.id, {
        type: values.type,
        quantityChange: values.quantityChange,
        note: values.note || undefined,
      });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["products"] });
      qc.invalidateQueries({ queryKey: ["stock-movements"] });
      toast.success(type === "IMPORT" ? "Đã nhập kho" : "Đã điều chỉnh tồn kho");
      onClose();
    },
  });

  return (
    <Dialog
      open={product !== null}
      onClose={onClose}
      title={type === "IMPORT" ? "Nhập kho" : "Điều chỉnh tồn kho"}
      description={product ? `${product.name} · đang còn ${product.stockQuantity} ${product.unit}` : undefined}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Huỷ
          </Button>
          <Button form="stock-form" type="submit" loading={submit.isPending}>
            {type === "IMPORT" ? "Nhập kho" : "Điều chỉnh"}
          </Button>
        </>
      }
    >
      <form
        id="stock-form"
        onSubmit={handleSubmit((v) => submit.mutate(v))}
        className="space-y-4"
        noValidate
      >
        <SelectField label="Loại biến động" {...register("type")}>
          <option value="IMPORT">Nhập kho — hàng về thêm</option>
          <option value="ADJUSTMENT">Điều chỉnh — sau kiểm kê</option>
        </SelectField>

        <TextField
          label="Số lượng"
          type="number"
          required
          hint={
            type === "ADJUSTMENT"
              ? "Nhập số âm nếu kiểm kê thấy thiếu, ví dụ -3"
              : "Số lượng hàng về"
          }
          error={errors.quantityChange?.message}
          {...register("quantityChange")}
        />

        {product && change !== 0 && (
          <p className="rounded-[var(--radius-control)] bg-paper px-3 py-2 text-sm">
            Tồn sau thao tác:{" "}
            <strong className={after < 0 ? "text-danger" : "text-ink"}>
              {after} {product.unit}
            </strong>
            {after < 0 && " — số này âm, backend sẽ từ chối"}
          </p>
        )}

        <TextAreaField
          label="Ghi chú"
          rows={2}
          hint="Số phiếu nhập, lý do kiểm kê lệch…"
          error={errors.note?.message}
          {...register("note")}
        />

        {submit.error && (
          <ErrorState
            message={
              submit.error instanceof ApiError ? submit.error.message : "Không cập nhật được tồn kho."
            }
          />
        )}
      </form>
    </Dialog>
  );
}
