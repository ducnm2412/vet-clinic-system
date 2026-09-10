"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Minus, Plus, Trash2 } from "lucide-react";
import { ApiError, cartApi, orderApi } from "@/lib/api";
import { formatPrice } from "@/lib/utils/format";
import { PageHeader } from "@/components/layout/DashboardShell";
import {
  Button,
  EmptyState,
  ErrorState,
  IconButton,
  Spinner,
  TextAreaField,
  TextField,
  useToast,
} from "@/components/ui";

const schema = z.object({
  recipientName: z.string().min(1, "Nhập tên người nhận").max(200),
  recipientPhone: z
    .string()
    .regex(/^0\d{9}$/, "Số điện thoại gồm 10 chữ số và bắt đầu bằng 0"),
  shippingAddress: z.string().min(1, "Nhập địa chỉ giao hàng").max(500),
  note: z.string().max(500).optional(),
});

type FormValues = z.infer<typeof schema>;

export default function CustomerCartPage() {
  const qc = useQueryClient();
  const toast = useToast();
  const router = useRouter();
  const [busyProduct, setBusyProduct] = useState<string | null>(null);

  const cart = useQuery({ queryKey: ["cart"], queryFn: cartApi.get });

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const changeQty = useMutation({
    mutationFn: async ({ productId, quantity }: { productId: string; quantity: number }) => {
      setBusyProduct(productId);
      return quantity <= 0
        ? cartApi.removeItem(productId)
        : cartApi.updateItem(productId, quantity);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["cart"] }),
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không cập nhật được giỏ hàng."),
    onSettled: () => setBusyProduct(null),
  });

  const clear = useMutation({
    mutationFn: cartApi.clear,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["cart"] });
      toast.success("Đã xoá sạch giỏ hàng");
    },
  });

  const checkout = useMutation({
    mutationFn: (values: FormValues) =>
      orderApi.checkout({ ...values, note: values.note || undefined, paymentMethod: "COD" }),
    onSuccess: (order) => {
      qc.invalidateQueries({ queryKey: ["cart"] });
      qc.invalidateQueries({ queryKey: ["orders"] });
      toast.success(`Đã đặt đơn ${order.orderCode}`);
      router.push(`/customer/orders/${order.id}`);
    },
    onError: (err) => {
      if (err instanceof ApiError && err.fieldErrors) {
        for (const [field, message] of Object.entries(err.fieldErrors)) {
          if (field in schema.shape) setError(field as keyof FormValues, { message });
        }
      } else {
        toast.error(err instanceof ApiError ? err.message : "Không đặt được hàng.");
      }
    },
  });

  if (cart.isLoading) return <Spinner label="Đang tải giỏ hàng" />;
  if (cart.isError) {
    return <ErrorState message="Không tải được giỏ hàng." onRetry={() => cart.refetch()} />;
  }

  const data = cart.data;
  if (!data || data.items.length === 0) {
    return (
      <>
        <PageHeader title="Giỏ hàng" />
        <div className="rounded-[var(--radius-control)] border border-line bg-surface">
          <EmptyState
            title="Giỏ hàng đang trống"
            description="Chọn vài món cho bé nhà bạn."
            action={
              <Link href="/customer/shop">
                <Button size="sm">Xem sản phẩm</Button>
              </Link>
            }
          />
        </div>
      </>
    );
  }

  return (
    <>
      <PageHeader title="Giỏ hàng" description={`${data.totalItems} món đang chờ đặt.`} />

      <div className="grid gap-5 lg:grid-cols-[1fr_22rem]">
        <div>
          <ul className="space-y-3">
            {data.items.map((item) => (
              <li
                key={item.productId}
                className="rounded-[var(--radius-control)] border border-line bg-surface p-4"
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-40 flex-1">
                    <p className="font-medium text-ink">{item.name}</p>
                    <p className="text-sm text-bark tnum">
                      {formatPrice(item.price)} / {item.unit}
                    </p>
                  </div>

                  <div className="flex items-center gap-2">
                    <IconButton
                      label={`Bớt một ${item.name}`}
                      variant="secondary"
                      size="sm"
                      disabled={busyProduct === item.productId}
                      onClick={() =>
                        changeQty.mutate({ productId: item.productId, quantity: item.quantity - 1 })
                      }
                    >
                      <Minus aria-hidden className="size-3.5" />
                    </IconButton>
                    <span className="w-8 text-center tnum">{item.quantity}</span>
                    <IconButton
                      label={`Thêm một ${item.name}`}
                      variant="secondary"
                      size="sm"
                      disabled={busyProduct === item.productId}
                      onClick={() =>
                        changeQty.mutate({ productId: item.productId, quantity: item.quantity + 1 })
                      }
                    >
                      <Plus aria-hidden className="size-3.5" />
                    </IconButton>
                  </div>

                  <div className="text-right">
                    <p className="font-medium tnum">{formatPrice(item.lineTotal)}</p>
                    <button
                      onClick={() => changeQty.mutate({ productId: item.productId, quantity: 0 })}
                      disabled={busyProduct === item.productId}
                      className="mt-1 inline-flex items-center gap-1 text-xs text-danger underline underline-offset-2"
                    >
                      <Trash2 aria-hidden className="size-3" />
                      Bỏ khỏi giỏ
                    </button>
                  </div>
                </div>

                {/* Tồn kho hỏi lại product-service mỗi lần tải giỏ, nên cảnh báo này luôn mới. */}
                {!item.available && (
                  <p className="mt-3 rounded-[var(--radius-control)] bg-danger-wash px-3 py-2 text-sm">
                    {item.unavailableReason ?? "Món này hiện không mua được."}
                  </p>
                )}
              </li>
            ))}
          </ul>

          <button
            onClick={() => clear.mutate()}
            className="mt-4 text-sm text-danger underline underline-offset-2"
          >
            Xoá sạch giỏ hàng
          </button>
        </div>

        <aside className="lg:sticky lg:top-6 lg:self-start">
          <form
            onSubmit={handleSubmit((v) => checkout.mutate(v))}
            className="space-y-4 rounded-[var(--radius-control)] border border-line bg-surface p-4"
            noValidate
          >
            <h2 className="font-medium text-ink">Giao tới đâu</h2>

            <TextField
              label="Người nhận"
              required
              error={errors.recipientName?.message}
              {...register("recipientName")}
            />
            <TextField
              label="Số điện thoại"
              inputMode="numeric"
              required
              hint="10 chữ số, bắt đầu bằng 0"
              error={errors.recipientPhone?.message}
              {...register("recipientPhone")}
            />
            <TextAreaField
              label="Địa chỉ"
              rows={2}
              required
              error={errors.shippingAddress?.message}
              {...register("shippingAddress")}
            />
            <TextField label="Ghi chú" error={errors.note?.message} {...register("note")} />

            <dl className="space-y-1 border-t border-line pt-3 text-sm">
              <div className="flex justify-between">
                <dt className="text-bark">Tạm tính</dt>
                <dd className="tnum font-medium">{formatPrice(data.subtotal)}</dd>
              </div>
              <p className="text-xs text-bark">Phí giao hàng cộng khi tạo đơn.</p>
            </dl>

            <p className="text-sm text-bark">Thanh toán khi nhận hàng.</p>

            {!data.checkoutable && (
              <p className="rounded-[var(--radius-control)] bg-amber-wash px-3 py-2 text-sm">
                Có món không mua được. Bỏ hoặc giảm số lượng rồi đặt lại.
              </p>
            )}

            <Button
              type="submit"
              size="lg"
              className="w-full"
              disabled={!data.checkoutable}
              loading={checkout.isPending}
            >
              Đặt hàng
            </Button>
          </form>
        </aside>
      </div>
    </>
  );
}
