"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ChevronRight, Minus, PawPrint, Plus, ShoppingBag, Trash2 } from "lucide-react";
import { Paw } from "@/components/site/Decor";
import { ApiError, cartApi, customerApi, orderApi, productApi } from "@/lib/api";
import { useCart } from "@/lib/useCart";
import { formatAddress, formatPrice } from "@/lib/utils/format";
import { goToPaymentUrl } from "@/lib/utils/payment";
import { useToast } from "@/components/ui";
import type { CartItem } from "@/types";
import { ArchPlaceholder, ButtonLink, Container, SiteButton } from "@/components/site/primitives";
import { CustomerOnly } from "@/components/site/CustomerOnly";
import { SiteInput, SiteTextarea } from "@/components/site/fields";

const ORANGE = "#f26b1d";
const GREEN = "#1f4a3c";
const INK = "#2b1d14";
const ORANGE_BTN = "!rounded-full !bg-[#f26b1d] !text-white hover:!bg-[#e05a10] !border-0";

const schema = z.object({
  recipientName: z.string().min(1, "Nhập tên người nhận").max(200),
  recipientPhone: z.string().regex(/^0\d{9}$/, "Số điện thoại gồm 10 chữ số và bắt đầu bằng 0"),
  shippingAddress: z.string().min(1, "Nhập địa chỉ giao hàng").max(500),
  note: z.string().max(500).optional(),
});

type FormValues = z.infer<typeof schema>;

type PayMethod = "COD" | "ONLINE";

const PAY_OPTIONS: Array<{ value: PayMethod; label: string; note: string }> = [
  { value: "COD", label: "Thanh toán khi nhận hàng", note: "Trả tiền mặt cho người giao." },
  {
    value: "ONLINE",
    label: "Thanh toán online",
    note: "Trả ngay sau khi đặt. Đơn được giữ trong thời gian ngắn, quá hạn chưa trả sẽ tự huỷ.",
  },
];

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
    <div className="relative overflow-hidden bg-[#fdf1e4] pb-24 md:pb-28">
      <Paw className="left-[2%] top-[22rem] size-9 rotate-12 text-[#f9c99a]/70" />
      <Paw className="right-[2%] top-[40rem] size-8 -rotate-12 text-[#f9c99a]/70" />
      <div className="mx-auto max-w-[1440px] px-4 sm:px-6">
        <nav className="flex flex-wrap items-center gap-2 pt-8 text-sm text-[#6b5a4e]">
          <Link href="/" className="hover:underline">Trang chủ</Link>
          <span>/</span>
          <span style={{ color: ORANGE }}>Giỏ hàng</span>
        </nav>

        <section className="relative mt-4 overflow-hidden rounded-[32px] bg-[radial-gradient(120%_140%_at_0%_0%,#fffaf4_0%,#ffeedd_45%,#ffd9b3_100%)] px-6 py-8 sm:px-10">
          <Paw className="right-[8%] top-6 size-12 rotate-12 text-[#f9a55c]" />
          <Paw className="bottom-5 right-[22%] size-8 -rotate-12 text-[#fbb983]" />
          <h1 className="flex items-center gap-3 text-[32px] font-extrabold sm:text-[40px]" style={{ color: GREEN }}>
            <ShoppingBag className="size-9" style={{ color: ORANGE }} /> Giỏ hàng của bạn
          </h1>
          <p className="mt-2 text-[16px]" style={{ color: INK }}>
            <b style={{ color: ORANGE }}>{data.totalItems} món</b> đang chờ bạn đặt. Giá và tồn kho được hỏi lại phòng khám mỗi lần mở trang này.
          </p>
        </section>

        <div className="mt-8 grid gap-8 lg:grid-cols-[1fr_26rem] lg:gap-10">
          <CartItems items={data.items} />
          <CheckoutPanel subtotal={data.subtotal} checkoutable={data.checkoutable} />
        </div>
      </div>
    </div>
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
      <ul className="space-y-4">
        {items.map((item) => (
          <li key={item.productId} className="rounded-3xl bg-[#fffaf3] p-4 shadow-[0_8px_24px_-16px_rgba(160,90,30,0.45)] sm:p-5">
            <div className="flex gap-5">
              <ItemThumb productId={item.productId} sku={item.sku} />

              <div className="min-w-0 flex-1">
                <h2 className="text-[18px] font-bold leading-snug" style={{ color: GREEN }}>
                  <Link href={`/products/${item.productId}`} className="hover:text-[#f26b1d]">
                    {item.name}
                  </Link>
                </h2>
                <p className="tnum mt-1 text-[#6b5a4e]">
                  {formatPrice(item.price)} mỗi {item.unit}
                </p>

                <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-3">
                  <div className="flex items-center gap-2">
                    <StepButton
                      label={`Bớt một ${item.name}`}
                      disabled={busy === item.productId}
                      onClick={() =>
                        change.mutate({ productId: item.productId, quantity: item.quantity - 1 })
                      }
                    >
                      <Minus aria-hidden className="size-4" />
                    </StepButton>
                    <span className="tnum grid h-10 w-11 place-items-center rounded-full bg-white font-semibold" style={{ color: INK }}>{item.quantity}</span>
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
                    className="inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-[15px] text-[#6b5a4e] transition hover:bg-[#fde0d6] hover:text-[#c2410c]"
                  >
                    <Trash2 aria-hidden className="size-4" />
                    Bỏ khỏi giỏ
                  </button>
                </div>
              </div>

              <p className="tnum shrink-0 text-right text-[19px] font-extrabold" style={{ color: ORANGE }}>
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
        <Link href="/products" className="inline-flex items-center gap-1.5 font-semibold" style={{ color: ORANGE }}>
          <PawPrint className="size-4 fill-current" /> Mua thêm món khác <ChevronRight className="size-4" />
        </Link>
        <button
          onClick={() => clear.mutate()}
          className="text-[15px] text-[#6b5a4e] underline underline-offset-4 hover:text-[#c2410c]"
        >
          Xoá sạch giỏ hàng
        </button>
      </div>
    </div>
  );
}

/** CartItem không có ảnh, nên hỏi product-service theo id (dùng chung cache với trang sản phẩm). */
function ItemThumb({ productId, sku }: { productId: string; sku: string }) {
  const product = useQuery({ queryKey: ["products", productId], queryFn: () => productApi.byId(productId) });
  const url = product.data?.imageUrl;
  return (
    <Link href={`/products/${productId}`} className="relative grid size-24 shrink-0 place-items-center overflow-hidden rounded-2xl bg-[#fbd9b8] sm:size-28">
      {url ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={url} alt="" className="size-[85%] object-contain mix-blend-multiply" />
      ) : (
        <ArchPlaceholder small tone={sku.length} className="h-20 w-16" />
      )}
    </Link>
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
      className="grid size-10 place-items-center rounded-full bg-[#f26b1d] text-white transition hover:brightness-95 disabled:opacity-35"
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
    setValue,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  // CN-10: sổ địa chỉ. Gọi hỏng hay chưa khai địa chỉ nào thì phần này biến mất, khách vẫn gõ
  // tay như trước — không chặn việc đặt hàng chỉ vì một tiện ích.
  const addresses = useQuery({ queryKey: ["addresses"], queryFn: customerApi.addresses });
  const saved = addresses.data ?? [];

  const [method, setMethod] = useState<PayMethod>("COD");

  const checkout = useMutation({
    mutationFn: async (values: FormValues) => {
      const order = await orderApi.checkout({
        ...values,
        note: values.note || undefined,
        paymentMethod: method,
      });
      if (method !== "ONLINE") return { order, paymentUrl: null };
      // Đơn đã tạo xong rồi mới xin link trả tiền. Xin hỏng thì khách vẫn có đơn và bấm "Thanh toán
      // ngay" được ở trang đơn, nên không coi cả lần đặt là thất bại.
      try {
        const init = await orderApi.startPayment(order.id);
        return { order, paymentUrl: init.paymentUrl };
      } catch {
        return { order, paymentUrl: null };
      }
    },
    onSuccess: ({ order, paymentUrl }) => {
      qc.invalidateQueries({ queryKey: ["cart"] });
      qc.invalidateQueries({ queryKey: ["orders"] });
      toast.success(`Đã đặt đơn ${order.orderCode}`);
      if (paymentUrl) {
        goToPaymentUrl(router, paymentUrl);
      } else {
        if (method === "ONLINE") {
          toast.error("Chưa mở được trang thanh toán. Bạn bấm \"Thanh toán ngay\" ở trang đơn nhé.");
        }
        router.push(`/user/orders/${order.id}`);
      }
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
        className="rounded-3xl bg-[#fffaf3] p-6 shadow-[0_10px_40px_-20px_rgba(160,90,30,0.45)] [&_input]:bg-white [&_textarea]:bg-white"
      >
        <h2 className="flex items-center gap-2 text-[22px] font-extrabold" style={{ color: GREEN }}>
          <PawPrint className="size-6 fill-current" style={{ color: ORANGE }} /> Giao tới đâu
        </h2>

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
          {saved.length > 0 && (
            <div className="sm:col-span-2">
              <p className="mb-2 text-[15px] font-medium">Địa chỉ đã lưu</p>
              <div className="flex flex-wrap gap-2">
                {saved.map((a) => (
                  <button
                    key={a.id}
                    type="button"
                    onClick={() =>
                      setValue("shippingAddress", formatAddress(a), { shouldValidate: true })
                    }
                    className="rounded-2xl border border-[#f0d9bf] bg-white px-3 py-1.5 text-left text-[15px] text-[#2b1d14] transition-colors hover:border-[#f26b1d]"
                  >
                    {formatAddress(a)}
                    {a.isDefault && <span className="ml-1.5 text-[#f26b1d]">· mặc định</span>}
                  </button>
                ))}
              </div>
              <p className="mt-2 text-[15px] text-stone">
                Bấm một địa chỉ để điền vào ô dưới, hoặc gõ địa chỉ khác.
              </p>
            </div>
          )}

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

        <fieldset className="mt-6 border-t border-[#f0e2d2] pt-5">
          <legend className="mb-3 text-[15px] font-medium">Cách thanh toán</legend>
          <div className="space-y-2">
            {PAY_OPTIONS.map((o) => (
              <label
                key={o.value}
                className={`flex cursor-pointer gap-3 rounded-[var(--radius-card)] border bg-white p-4 transition-colors ${
                  method === o.value ? "border-[#f26b1d] bg-[#fff3e8]" : "border-[#f0e2d2] hover:border-[#f26b1d]"
                }`}
              >
                <input
                  type="radio"
                  name="paymentMethod"
                  value={o.value}
                  checked={method === o.value}
                  onChange={() => setMethod(o.value)}
                  className="mt-1 size-4 accent-[#f26b1d]"
                />
                <span>
                  <span className="block font-medium">{o.label}</span>
                  <span className="block text-[15px] text-stone">{o.note}</span>
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        <dl className="mt-6 space-y-2 rounded-2xl bg-[#dcebc9] px-5 py-4">
          <div className="flex justify-between">
            <dt className="font-semibold" style={{ color: GREEN }}>Tạm tính</dt>
            <dd className="tnum text-[20px] font-extrabold" style={{ color: ORANGE }}>{formatPrice(subtotal)}</dd>
          </div>
          {/*
            Không đoán phí giao. order-service chỉ tính `shippingFee` lúc tạo đơn, nên nói
            đúng như vậy còn hơn hiện một con số rồi lệch với đơn thật.
          */}
          <div className="flex justify-between gap-4 text-[14px] text-[#3b2a20]">
            <dt>Phí giao hàng</dt>
            <dd>Tính khi phòng khám nhận đơn</dd>
          </div>
        </dl>

        {!checkoutable && (
          <p className="mt-5 rounded-2xl bg-[#fde0d6] px-4 py-3 text-[15px] text-coral-deep">
            Có món trong giỏ hiện không mua được. Bỏ hoặc giảm số lượng món đó rồi đặt lại.
          </p>
        )}

        <SiteButton
          type="submit"
          size="lg"
          className={`mt-6 w-full ${ORANGE_BTN}`}
          disabled={!checkoutable}
          loading={checkout.isPending}
        >
          {method === "ONLINE" ? "Đặt hàng và thanh toán" : "Đặt hàng"}
        </SiteButton>

        <p className="mt-4 text-center text-[15px] text-stone">
          {method === "ONLINE"
            ? "Sau khi đặt, bạn được chuyển sang trang thanh toán."
            : "Thanh toán tiền mặt khi nhận hàng."}
        </p>
      </form>
    </aside>
  );
}

function EmptyCart() {
  return (
    <Container className="py-20 pb-28 text-center md:py-28">
      <div className="mx-auto grid size-40 place-items-center rounded-full bg-[#fde4cc]">
        <ShoppingBag className="size-16" style={{ color: ORANGE }} />
      </div>
      <h1 className="mt-8 text-[32px] font-extrabold" style={{ color: GREEN }}>Giỏ hàng đang trống</h1>
      <p className="mx-auto mt-4 max-w-md text-[#6b5a4e]">
        Chọn vài món cho bé nhà bạn. Hạt, sữa tắm, vòng cổ đều có sẵn tại phòng khám.
      </p>
      <div className="mt-8">
        <ButtonLink href="/products" size="lg" className={ORANGE_BTN}>
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
