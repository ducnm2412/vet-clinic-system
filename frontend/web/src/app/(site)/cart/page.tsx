"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Minus, Plus, Trash2 } from "lucide-react";
import { ApiError, cartApi, orderApi } from "@/lib/api";
import { useCart } from "@/lib/useCart";
import { formatPrice } from "@/lib/utils/format";
import { useToast } from "@/components/ui";
import type { CartItem } from "@/types";
import { ArchPlaceholder, ButtonLink, Container, SiteButton } from "@/components/site/primitives";
import { CustomerOnly } from "@/components/site/CustomerOnly";
import { SiteInput, SiteTextarea } from "@/components/site/fields";

const schema = z.object({
  recipientName: z.string().min(1, "Nhập tên người nhận").max(200),
  recipientPhone: z.string().regex(/^0\d{9}$/, "Số điện thoại gồm 10 chữ số và bắt đầu bằng 0"),
  shippingAddress: z.string().min(1, "Nhập địa chỉ giao hàng").max(500),
  note: z.string().max(500).optional(),
});

type FormValues = z.infer<typeof schema>;

export default function CartPage() {
  return (
    <CustomerOnly>
      <CartBody />
    </CustomerOnly>
  );
}

function CartBody() {
  const cart = useCart();

  if (cart.isLoading) return <CartSkeleton />;

  if (cart.isError) {
    return (
      <Container className="py-20 text-center">
        <h1 className="t-h2">Chưa mở được giỏ hàng</h1>
        <p className="measure mx-auto mt-4 text-stone">
          Kết nối tới phòng khám đang trục trặc. Giỏ hàng của bạn vẫn còn nguyên.
        </p>
        <SiteButton variant="outline" className="mt-8" onClick={() => cart.refetch()}>
          Thử lại
        </SiteButton>
      </Container>
    );
  }

  const data = cart.data;
  if (!data || data.items.length === 0) return <EmptyCart />;

  return (
    <Container className="py-12 md:py-16">
      <h1 className="t-h2">Giỏ hàng</h1>
      <p className="mt-3 text-stone">
        {data.totalItems} món đang chờ bạn đặt. Giá và tồn kho được hỏi lại phòng khám mỗi lần
        mở trang này.
      </p>

      <div className="mt-10 grid gap-12 lg:grid-cols-[1fr_23rem] lg:gap-16">
        <CartItems items={data.items} />
        <CheckoutPanel subtotal={data.subtotal} checkoutable={data.checkoutable} />
      </div>
    </Container>
  );
}

function CartItems({ items }: { items: CartItem[] }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [busy, setBusy] = useState<string | null>(null);

  const change = useMutation({
    mutationFn: async ({ productId, quantity }: { productId: string; quantity: number }) => {
      setBusy(productId);
      return quantity <= 0 ? cartApi.removeItem(productId) : cartApi.updateItem(productId, quantity);
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["cart"] }),
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không cập nhật được giỏ hàng."),
    onSettled: () => setBusy(null),
  });

  const clear = useMutation({
    mutationFn: cartApi.clear,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["cart"] });
      toast.success("Đã xoá sạch giỏ hàng");
    },
  });

  return (
    <div>
      <ul className="divide-y divide-mist border-y border-mist">
        {items.map((item) => (
          <li key={item.productId} className="py-6">
            <div className="flex gap-5">
              {/*
                CartItem không có ảnh — order-service chỉ chụp lại tên, giá và đơn vị lúc
                thêm vào giỏ. Nên đây là mái vòm màu, cùng mô-típ với cả trang.
              */}
              <ArchPlaceholder
                small
                tone={item.sku.length}
                className="h-24 w-20 shrink-0"
              />

              <div className="min-w-0 flex-1">
                <h2 className="font-[family-name:var(--font-brand)] text-[19px] font-semibold leading-snug">
                  <Link href={`/products/${item.productId}`} className="hover:text-teal-deep">
                    {item.name}
                  </Link>
                </h2>
                <p className="tnum mt-1 text-stone">
                  {formatPrice(item.price)} mỗi {item.unit}
                </p>

                <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-3">
                  <div className="flex items-center gap-1 rounded-full border border-mist p-1">
                    <StepButton
                      label={`Bớt một ${item.name}`}
                      disabled={busy === item.productId}
                      onClick={() =>
                        change.mutate({ productId: item.productId, quantity: item.quantity - 1 })
                      }
                    >
                      <Minus aria-hidden className="size-4" />
                    </StepButton>
                    <span className="tnum w-9 text-center font-medium">{item.quantity}</span>
                    <StepButton
                      label={`Thêm một ${item.name}`}
                      disabled={busy === item.productId}
                      onClick={() =>
                        change.mutate({ productId: item.productId, quantity: item.quantity + 1 })
                      }
                    >
                      <Plus aria-hidden className="size-4" />
                    </StepButton>
                  </div>

                  <button
                    onClick={() => change.mutate({ productId: item.productId, quantity: 0 })}
                    disabled={busy === item.productId}
                    className="inline-flex items-center gap-1.5 text-[15px] text-stone underline underline-offset-4 hover:text-coral-deep"
                  >
                    <Trash2 aria-hidden className="size-4" />
                    Bỏ khỏi giỏ
                  </button>
                </div>
              </div>

              <p className="tnum shrink-0 text-right text-[17px] font-semibold">
                {formatPrice(item.lineTotal)}
              </p>
            </div>

            {!item.available && (
              <p className="mt-4 rounded-[var(--radius-card)] bg-peach px-4 py-3 text-[15px] text-coral-deep">
                {item.unavailableReason ?? "Món này hiện không mua được."}
              </p>
            )}
          </li>
        ))}
      </ul>

      <div className="mt-6 flex flex-wrap items-center justify-between gap-4">
        <Link href="/products" className="text-[15px] text-teal-deep underline underline-offset-4">
          Mua thêm món khác
        </Link>
        <button
          onClick={() => clear.mutate()}
          className="text-[15px] text-stone underline underline-offset-4 hover:text-coral-deep"
        >
          Xoá sạch giỏ hàng
        </button>
      </div>
    </div>
  );
}

function StepButton({
  label,
  disabled,
  onClick,
  children,
}: {
  label: string;
  disabled?: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className="grid size-10 place-items-center rounded-full text-pine transition-colors hover:bg-mint disabled:opacity-35"
    >
      {children}
    </button>
  );
}

function CheckoutPanel({ subtotal, checkoutable }: { subtotal: number; checkoutable: boolean }) {
  const qc = useQueryClient();
  const toast = useToast();
  const router = useRouter();

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const checkout = useMutation({
    mutationFn: (values: FormValues) =>
      orderApi.checkout({ ...values, note: values.note || undefined, paymentMethod: "COD" }),
    onSuccess: (order) => {
      qc.invalidateQueries({ queryKey: ["cart"] });
      qc.invalidateQueries({ queryKey: ["orders"] });
      toast.success(`Đã đặt đơn ${order.orderCode}`);
      router.push(`/orders/${order.id}`);
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

  return (
    <aside className="lg:sticky lg:top-24 lg:self-start">
      <form
        onSubmit={handleSubmit((v) => checkout.mutate(v))}
        noValidate
        className="rounded-[var(--radius-card)] border border-mist bg-mint p-6"
      >
        <h2 className="t-h3">Giao tới đâu</h2>

        <div className="mt-5 space-y-4">
          <SiteInput
            label="Người nhận"
            required
            autoComplete="name"
            error={errors.recipientName?.message}
            {...register("recipientName")}
          />
          <SiteInput
            label="Số điện thoại"
            required
            inputMode="numeric"
            autoComplete="tel"
            hint="10 chữ số, bắt đầu bằng 0"
            error={errors.recipientPhone?.message}
            {...register("recipientPhone")}
          />
          <SiteTextarea
            label="Địa chỉ"
            required
            rows={2}
            autoComplete="street-address"
            error={errors.shippingAddress?.message}
            {...register("shippingAddress")}
          />
          <SiteInput
            label="Ghi chú cho người giao"
            error={errors.note?.message}
            {...register("note")}
          />
        </div>

        <dl className="mt-6 space-y-2 border-t border-mist pt-5">
          <div className="flex justify-between">
            <dt className="text-stone">Tạm tính</dt>
            <dd className="tnum font-semibold">{formatPrice(subtotal)}</dd>
          </div>
          {/*
            Không đoán phí giao. order-service chỉ tính `shippingFee` lúc tạo đơn, nên nói
            đúng như vậy còn hơn hiện một con số rồi lệch với đơn thật.
          */}
          <div className="flex justify-between text-[15px] text-stone">
            <dt>Phí giao hàng</dt>
            <dd>Tính khi phòng khám nhận đơn</dd>
          </div>
        </dl>

        {!checkoutable && (
          <p className="mt-5 rounded-[var(--radius-card)] bg-white px-4 py-3 text-[15px] text-coral-deep">
            Có món trong giỏ hiện không mua được. Bỏ hoặc giảm số lượng món đó rồi đặt lại.
          </p>
        )}

        <SiteButton
          type="submit"
          size="lg"
          className="mt-6 w-full"
          disabled={!checkoutable}
          loading={checkout.isPending}
        >
          Đặt hàng
        </SiteButton>

        <p className="mt-4 text-center text-[15px] text-stone">
          Thanh toán tiền mặt khi nhận hàng.
        </p>
      </form>
    </aside>
  );
}

function EmptyCart() {
  return (
    <Container className="py-20 text-center md:py-28">
      <ArchPlaceholder className="mx-auto h-56 w-44" />
      <h1 className="t-h2 mt-10">Giỏ hàng đang trống</h1>
      <p className="measure mx-auto mt-4 text-stone">
        Chọn vài món cho bé nhà bạn. Hạt, sữa tắm, vòng cổ đều có sẵn tại phòng khám.
      </p>
      <div className="mt-8">
        <ButtonLink href="/products" size="lg">
          Xem sản phẩm
        </ButtonLink>
      </div>
    </Container>
  );
}

function CartSkeleton() {
  return (
    <Container className="py-12 md:py-16">
      <div className="h-10 w-48 animate-pulse rounded-full bg-mint" />
      <div className="mt-10 grid gap-12 lg:grid-cols-[1fr_23rem]">
        <ul className="space-y-6">
          {[0, 1].map((i) => (
            <li key={i} className="flex gap-5">
              <div className="arch-sm h-24 w-20 shrink-0 animate-pulse bg-mint" />
              <div className="flex-1 space-y-3 pt-2">
                <div className="h-5 w-2/3 animate-pulse rounded bg-mint" />
                <div className="h-4 w-1/3 animate-pulse rounded bg-mint" />
              </div>
            </li>
          ))}
        </ul>
        <div className="h-96 animate-pulse rounded-[var(--radius-card)] bg-mint" />
      </div>
    </Container>
  );
}
