"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Check, ChevronRight, MapPin, Package, PawPrint, Printer, ShoppingCart, Truck } from "lucide-react";
import { ApiError, orderApi, productApi } from "@/lib/api";
import { formatDateTime, formatPrice } from "@/lib/utils/format";
import { ORDER_PAYMENT_METHOD } from "@/lib/utils/status";
import { goToPaymentUrl } from "@/lib/utils/payment";
import { cn } from "@/lib/utils/cn";
import { useToast } from "@/components/ui";
import { CLINIC } from "@/config/clinic";
import type { Order, OrderStatus } from "@/types";
import { ButtonLink, Container, SiteButton } from "@/components/site/primitives";
import { CustomerOnly } from "@/components/site/CustomerOnly";
import { SiteDialog } from "@/components/site/SiteDialog";
import { SiteTextarea } from "@/components/site/fields";
import { orderLook, StatusPill } from "@/components/site/StatusPill";

export default function OrderDetailPage() {
  return (
    <CustomerOnly>
      <OrderDetailBody />
    </CustomerOnly>
  );
}

function OrderDetailBody() {
  const { id } = useParams<{ id: string }>();

  const order = useQuery({
    queryKey: ["orders", id],
    queryFn: () => orderApi.byId(id),
    // Đơn đang chờ trả: kiểm lại thưa thớt để thấy ngay khi đã thanh toán, bị tự huỷ quá hạn, hoặc
    // nhân viên đã thêm sản phẩm vào hoá đơn quầy.
    refetchInterval: (q) => {
      if (isAwaitingPayment(q.state.data)) return 15_000;
      return isAwaitingCounter(q.state.data) ? 30_000 : false;
    },
  });

  if (order.isLoading) {
    return (
      <Container className="py-20">
        <div className="h-80 animate-pulse rounded-[var(--radius-card)] bg-mint" />
      </Container>
    );
  }

  if (order.isError || !order.data) {
    return (
      <Container className="py-24 text-center">
        <h1 className="t-h2">Không mở được đơn này</h1>
        <p className="measure mx-auto mt-4 text-stone">
          Đơn có thể không thuộc tài khoản của bạn, hoặc đường dẫn bị sai.
        </p>
        <div className="mt-8">
          <ButtonLink href="/user/orders" variant="outline">
            Về danh sách đơn
          </ButtonLink>
        </div>
      </Container>
    );
  }

  return <OrderBody order={order.data} />;
}

/** Đơn thanh toán online mà khách chưa trả và còn trong hạn xử lý. */
function isAwaitingPayment(order: Order | undefined): boolean {
  return (
    order !== undefined &&
    order.paymentMethod === "ONLINE" &&
    order.paymentStatus === "UNPAID" &&
    order.status === "PENDING"
  );
}

/** Hoá đơn quầy nhân viên đã lập, khách chưa ghé trả. Khách không tự trả hay huỷ được. */
function isAwaitingCounter(order: Order | undefined): boolean {
  return (
    order !== undefined &&
    order.channel === "COUNTER" &&
    order.paymentStatus === "UNPAID" &&
    order.status === "PENDING"
  );
}

function OrderBody({ order }: { order: Order }) {
  const counter = order.channel === "COUNTER";
  const awaitingCounter = isAwaitingCounter(order);
  const look = orderLook(order);

  return (
    <div>
      {/* Breadcrumb */}
      <nav aria-label="Breadcrumb" className="mb-4 flex flex-wrap items-center gap-2 text-[13px] text-[#6b5a4e]">
        <Link href="/" className="hover:text-[#f57c1f]">Trang chủ</Link>
        <ChevronRight aria-hidden className="size-3.5" />
        <Link href="/user/orders" className="hover:text-[#f57c1f]">Đơn hàng của tôi</Link>
        <ChevronRight aria-hidden className="size-3.5" />
        <span className="text-[#2b1d14]">Chi tiết đơn hàng</span>
      </nav>

      <div className="rounded-[28px] bg-white p-4 shadow-[0_4px_30px_-14px_rgba(90,45,10,0.25)] sm:p-6">
        {/* Đầu trang: quay lại, mã đơn, trạng thái, in hoá đơn */}
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <Link
              href="/user/orders"
              className="inline-flex h-9 items-center gap-1.5 rounded-xl border border-[#f7b98a] bg-[#fff1e3] px-4 text-sm font-semibold text-[#f57c1f] transition hover:bg-[#ffe3c9]"
            >
              <ArrowLeft aria-hidden className="size-4" /> Quay lại
            </Link>
            <div className="mt-4 flex flex-wrap items-center gap-3">
              <h1 className="text-[26px] font-extrabold text-[#2b1d14] sm:text-[30px]">
                {counter ? "Hoá đơn" : "Đơn hàng"} #{order.orderCode.replace(/^#/, "")}
              </h1>
              <StatusPill look={look} />
            </div>
            <p className="mt-1 text-[15px] text-[#6b5a4e]">
              {counter ? "Lập tại quầy" : "Đặt ngày"}: {formatDateTime(order.createdAt)}
            </p>
          </div>
          <button
            type="button"
            onClick={() => window.print()}
            className="inline-flex h-10 items-center gap-2 rounded-xl border border-[#f57c1f] bg-white px-4 text-sm font-semibold text-[#f57c1f] transition hover:bg-[#fff1e3] print:hidden"
          >
            <Printer aria-hidden className="size-4" /> In hoá đơn
          </button>
        </div>

        {awaitingCounter && (
          <Notice>
            Phòng khám đã lập hoá đơn này sau buổi khám của bạn. Bạn ghé quầy để thanh toán; nếu muốn mua thêm sản
            phẩm, nhân viên sẽ thêm vào hoá đơn giúp bạn. Số tiền cuối cùng được chốt khi thanh toán.
          </Notice>
        )}
        {order.status === "CANCELLED" && order.cancelReason && (
          <Notice tone="danger">Lý do huỷ: {order.cancelReason}</Notice>
        )}
        {order.refundRequired && (
          <Notice>
            Đơn này bạn đã thanh toán trước khi bị huỷ. Phòng khám sẽ hoàn tiền lại cho bạn; gọi {CLINIC.phone} nếu
            cần biết thêm.
          </Notice>
        )}

        {/* Hoá đơn tại quầy được thu ngay nên không có chặng xác nhận và giao hàng để kể. */}
        {!counter && (
          <div className="mt-6">
            <Timeline order={order} />
          </div>
        )}

        {/* Thông tin đơn + địa chỉ */}
        <div className="mt-6 grid gap-5 lg:grid-cols-[1.15fr_1fr]">
          <section className="rounded-2xl border border-[#f3ebe3] bg-white p-5 shadow-[0_2px_14px_-8px_rgba(0,0,0,0.12)]">
            <h2 className="border-b border-[#f3ebe3] pb-3 text-lg font-bold text-[#2b1d14]">Thông tin đơn hàng</h2>
            <dl className="mt-3 space-y-3 text-[15px]">
              <InfoRow label="Mã đơn hàng">#{order.orderCode.replace(/^#/, "")}</InfoRow>
              <InfoRow label="Phương thức thanh toán">
                {order.paymentMethod ? ORDER_PAYMENT_METHOD[order.paymentMethod] : "—"}
                {order.paymentMethod === "ONLINE" && (
                  <span className="text-[#6b5a4e]"> · {order.paymentStatus === "PAID" ? "đã thanh toán" : "chưa thanh toán"}</span>
                )}
              </InfoRow>
              <InfoRow label={counter ? "Hình thức" : "Phương thức vận chuyển"}>
                {counter ? "Thanh toán tại quầy" : "Giao hàng tận nơi"}
              </InfoRow>
              {order.transferReference && <InfoRow label="Mã giao dịch">{order.transferReference}</InfoRow>}
              {order.paidAt && (
                <InfoRow label="Đã thanh toán lúc">
                  <span className="tnum">{formatDateTime(order.paidAt)}</span>
                </InfoRow>
              )}
              <InfoRow label="Trạng thái">
                <StatusPill look={look} />
              </InfoRow>
            </dl>
          </section>

          <section className="rounded-2xl border border-[#f3ebe3] bg-white p-5 shadow-[0_2px_14px_-8px_rgba(0,0,0,0.12)]">
            <h2 className="text-lg font-bold text-[#2b1d14]">{counter ? "Khách hàng" : "Địa chỉ giao hàng"}</h2>
            <div className="mt-4 flex gap-4">
              <div className="min-w-0 flex-1">
                <p className="flex items-center gap-2 font-semibold text-[#2b1d14]">
                  <MapPin aria-hidden className="size-4 shrink-0 fill-[#2b1d14] text-white" /> {order.recipientName}
                </p>
                {!counter && (
                  <>
                    <p className="tnum mt-1 pl-6 text-[13px] text-[#6b5a4e]">{order.recipientPhone}</p>
                    <p className="mt-1 pl-6 text-[13px] leading-relaxed text-[#6b5a4e]">{order.shippingAddress}</p>
                  </>
                )}
                {order.note && <p className="mt-2 pl-6 text-[13px] italic text-[#6b5a4e]">Ghi chú: {order.note}</p>}
              </div>
              {!counter && <MapThumb />}
            </div>
          </section>
        </div>

        {/* Sản phẩm */}
        <section className="mt-8">
          <h2 className="flex items-center gap-2 text-lg font-bold text-[#2b1d14]">
            <PawPrint aria-hidden className="size-6 fill-[#f57c1f] text-[#f57c1f]" />
            {counter ? "Nội dung hoá đơn" : "Sản phẩm trong đơn hàng"}
          </h2>
          <div className="mt-4 overflow-hidden rounded-2xl border border-[#f3ebe3]">
            <ul className="divide-y divide-[#f3ebe3] px-4">
              {order.examAmount > 0 && (
                <li className="flex items-center gap-4 py-4">
                  <span className="grid size-[72px] shrink-0 place-items-center rounded-xl bg-[#fdf1e4] text-[#f7a964]">
                    <Package className="size-8" />
                  </span>
                  <p className="min-w-0 flex-1 font-medium text-[#2b1d14]">Tiền khám / thuốc</p>
                  <p className="tnum font-bold text-[#2b1d14]">{formatPrice(order.examAmount)}</p>
                </li>
              )}
              {order.items.map((item) => (
                <li
                  key={item.productId}
                  className="grid grid-cols-[64px_1fr] items-center gap-x-3 py-4 sm:grid-cols-[72px_1fr_110px_60px_110px] sm:gap-x-4"
                >
                  <ItemThumb productId={item.productId} />
                  {/* Điện thoại: tên (tối đa 2 dòng) ở trên, số lượng và thành tiền chung một dòng ở dưới. */}
                  <div className="min-w-0 sm:contents">
                    <Link href={`/products/${item.productId}`} className="line-clamp-2 text-[15px] text-[#2b1d14] hover:text-[#f57c1f] sm:line-clamp-none">
                      {item.productName}
                    </Link>
                    <div className="mt-1 flex items-baseline justify-between gap-2 sm:hidden">
                      <span className="tnum text-[13px] text-[#6b5a4e]">× {item.quantity}</span>
                      <span className="tnum whitespace-nowrap font-bold text-[#2b1d14]">{formatPrice(item.lineTotal)}</span>
                    </div>
                  </div>
                  <p className="tnum hidden text-right font-bold text-[#2b1d14] sm:block">{formatPrice(item.unitPrice)}</p>
                  <p className="tnum hidden text-center text-[#2b1d14] sm:block">× {item.quantity}</p>
                  <p className="tnum hidden text-right font-bold text-[#2b1d14] sm:block">{formatPrice(item.lineTotal)}</p>
                </li>
              ))}
            </ul>
            <dl className="ml-auto max-w-xs space-y-2 border-t border-[#f3ebe3] px-5 py-4 text-sm sm:max-w-sm">
              {order.items.length > 0 && <Money label="Tạm tính" value={order.subtotal} />}
              {order.examAmount > 0 && <Money label="Tiền khám / thuốc" value={order.examAmount} />}
              {!counter && <Money label="Phí vận chuyển" value={order.shippingFee} />}
            </dl>
            <div className="flex items-center justify-between gap-3 bg-[#fdecdc] px-5 py-4 sm:justify-end sm:gap-10">
              <span className="whitespace-nowrap font-bold text-[#2b1d14]">{awaitingCounter ? "Cần thanh toán" : "Tổng thanh toán"}</span>
              <span className="tnum whitespace-nowrap text-[20px] font-extrabold text-[#f57c1f] sm:text-[24px]">{formatPrice(order.total)}</span>
            </div>
          </div>
        </section>

        {/* Thanh toán / huỷ */}
        <div className={cn("mt-6 grid gap-5 print:hidden", isAwaitingPayment(order) && "md:grid-cols-2")}>
          {isAwaitingPayment(order) && <PayNowPanel order={order} />}
          {counter ? (
            <div className="text-[15px] text-[#6b5a4e]">
              {awaitingCounter
                ? "Hoá đơn này do nhân viên xử lý tại quầy nên bạn không tự thanh toán hay huỷ trên web được."
                : order.status === "CANCELLED"
                  ? "Hoá đơn này đã được phòng khám huỷ."
                  : "Hoá đơn này đã thanh toán xong tại quầy."}{" "}
              Gọi {CLINIC.phone} nếu bạn cần phòng khám hỗ trợ.
            </div>
          ) : (
            <CancelPanel order={order} />
          )}
        </div>
      </div>
    </div>
  );
}

function Notice({ children, tone }: { children: React.ReactNode; tone?: "danger" }) {
  return (
    <p className={cn("mt-4 rounded-2xl bg-[#fdf6ee] px-5 py-3 text-[15px]", tone === "danger" ? "text-coral-deep" : "text-[#3b2a20]")}>
      {children}
    </p>
  );
}

function InfoRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    // Điện thoại: nhãn trái, giá trị dồn phải trên cùng một dòng. Màn rộng: hai cột có dấu ":".
    <div className="flex items-center justify-between gap-4 sm:grid sm:grid-cols-[minmax(0,11rem)_1fr] sm:gap-3">
      <dt className="shrink-0 text-[#6b5a4e] sm:text-[#3b2a20]">{label}</dt>
      <dd className="min-w-0 text-right font-medium text-[#2b1d14] sm:text-left">
        <span className="mr-1 hidden text-[#6b5a4e] sm:inline">:</span> {children}
      </dd>
    </div>
  );
}

/** Ô bản đồ minh hoạ (không phải bản đồ thật — đơn hàng không lưu toạ độ). */
function MapThumb() {
  return (
    <div aria-hidden className="relative hidden h-28 w-36 shrink-0 overflow-hidden rounded-xl bg-[#eef1f4] sm:block">
      <svg viewBox="0 0 144 112" className="absolute inset-0 size-full">
        <path d="M0 30 L144 52 M0 80 L144 70 M40 0 L55 112 M100 0 L90 112 M0 105 L60 60 L144 20" stroke="#fff" strokeWidth="5" fill="none" />
        <path d="M0 55 Q40 40 70 90 T144 95" stroke="#bfe0f2" strokeWidth="8" fill="none" />
      </svg>
      <MapPin className="absolute left-1/2 top-1/2 size-8 -translate-x-1/2 -translate-y-[70%] fill-[#f57c1f] text-white" />
    </div>
  );
}

/** Giây còn lại tới hạn; null khi không có hạn. Chạy mỗi giây để đồng hồ đếm ngược. */
function useSecondsLeft(iso: string | null): number | null {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!iso) return;
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, [iso]);
  if (!iso) return null;
  return Math.max(0, Math.floor((new Date(iso).getTime() - now) / 1000));
}

function formatCountdown(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

function PayNowPanel({ order }: { order: Order }) {
  const router = useRouter();
  const toast = useToast();
  const left = useSecondsLeft(order.paymentExpiresAt);
  const expired = left !== null && left <= 0;

  const pay = useMutation({
    mutationFn: () => orderApi.startPayment(order.id),
    onSuccess: (init) => goToPaymentUrl(router, init.paymentUrl),
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Chưa mở được trang thanh toán."),
  });

  return (
    <div className="rounded-2xl border border-[#f7b98a] bg-white p-5">
      <h2 className="t-h3">Thanh toán đơn này</h2>
      <p className="tnum mt-3 text-[21px] font-semibold">{formatPrice(order.total)}</p>
      {expired ? (
        <p className="mt-3 text-coral-deep">
          Đơn đã hết hạn thanh toán và sẽ được huỷ tự động trong ít phút.
        </p>
      ) : (
        <>
          <p className="mt-3 text-stone">
            Phòng khám xử lý đơn sau khi nhận được tiền.
            {left !== null && (
              <>
                {" "}Còn <span className="tnum font-medium text-pine">{formatCountdown(left)}</span> để
                thanh toán, quá hạn đơn sẽ tự huỷ.
              </>
            )}
          </p>
          <SiteButton
            size="lg"
            className="mt-5 w-full"
            loading={pay.isPending}
            onClick={() => pay.mutate()}
          >
            Thanh toán ngay
          </SiteButton>
        </>
      )}
    </div>
  );
}

/** OrderItem không có ảnh, nên hỏi product-service theo id (dùng chung cache với trang sản phẩm). */
function ItemThumb({ productId }: { productId: string }) {
  const product = useQuery({ queryKey: ["products", productId], queryFn: () => productApi.byId(productId), retry: false });
  const url = product.data?.imageUrl;
  return (
    <Link href={`/products/${productId}`} className="grid size-16 shrink-0 sm:size-[72px] place-items-center overflow-hidden rounded-xl bg-[#fdf1e4]">
      {url ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={url} alt="" className="size-[85%] object-contain mix-blend-multiply" />
      ) : (
        <Package className="size-8 text-[#f7a964]" />
      )}
    </Link>
  );
}

function Money({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex justify-between">
      <dt className="text-stone">{label}</dt>
      <dd className="tnum">{formatPrice(value)}</dd>
    </div>
  );
}

const FLOW: Array<{ status: OrderStatus; label: string; note: string }> = [
  { status: "PENDING", label: "Đặt hàng", note: "Phòng khám nhận được đơn." },
  { status: "CONFIRMED", label: "Đang xử lý", note: "Phòng khám đã gom hàng cho bạn." },
  { status: "SHIPPING", label: "Đang giao", note: "Hàng đang trên đường tới bạn." },
  { status: "COMPLETED", label: "Giao hàng thành công", note: "Bạn đã nhận và trả tiền." },
];

/** Ở chặng đầu, đơn online cần nói rõ đang chờ tiền hay đã có tiền. */
function pendingNote(order: Order): string {
  if (order.paymentMethod !== "ONLINE") return FLOW[0].note;
  return order.paymentStatus === "PAID"
    ? "Phòng khám đã nhận được tiền, đang chờ xác nhận đơn."
    : "Đơn được giữ chờ bạn thanh toán online.";
}

/**
 * Đường đi của đơn. Mốc thời gian lấy từ GET /orders/{id}/history — chỉ hiện giờ cho
 * bước nào đã thật sự xảy ra, không đoán ngày cho bước chưa tới.
 */
function Timeline({ order }: { order: Order }) {
  const history = useQuery({
    queryKey: ["orders", order.id, "history"],
    queryFn: () => orderApi.history(order.id),
  });

  const at = new Map<OrderStatus, string>();
  for (const h of history.data ?? []) {
    if (!at.has(h.toStatus)) at.set(h.toStatus, h.createdAt);
  }
  // Bước đầu không phải lúc nào cũng có trong lịch sử — chính là lúc tạo đơn.
  if (!at.has("PENDING")) at.set("PENDING", order.createdAt);

  if (order.status === "CANCELLED") {
    return (
      <section className="rounded-2xl bg-[#fdf6ee] p-5">
        <h2 className="t-h3">Đơn đã dừng lại</h2>
        <p className="measure mt-3 text-stone">
          Đơn này bị huỷ {order.cancelledAt ? `lúc ${formatDateTime(order.cancelledAt)}` : ""}. Nếu
          bạn vẫn cần hàng, đặt lại giúp phòng khám nhé.
        </p>
        <div className="mt-6">
          <ButtonLink href="/products" variant="outline">
            Đặt lại
          </ButtonLink>
        </div>
      </section>
    );
  }

  const current = FLOW.findIndex((f) => f.status === order.status);

  return (
    <section aria-label="Tiến trình đơn hàng">
      <ol className="grid grid-cols-4">
        {FLOW.map((stage, i) => {
          const done = i <= current;
          const isNow = i === current;
          const last = i === FLOW.length - 1;
          const time = at.get(stage.status);
          const Icon = STAGE_ICONS[i];
          return (
            <li key={stage.status} className="relative flex flex-col items-center px-1 text-center">
              {!last && (
                <span aria-hidden className={cn("absolute left-1/2 top-6 h-[3px] w-full", i < current ? "bg-[#1f7a4d]" : "bg-[#e8e1da]")} />
              )}
              <span
                aria-hidden
                title={stage.status === "PENDING" ? pendingNote(order) : stage.note}
                className={cn(
                  "relative grid size-12 place-items-center rounded-full",
                  !done && "border-2 border-[#e8e1da] bg-white text-[#b5a89c]",
                  done && !(isNow && last) && "bg-[#1f7a4d] text-white",
                  isNow && last && "bg-[#f57c1f] text-white ring-4 ring-[#f57c1f]/25",
                  isNow && !last && "ring-4 ring-[#1f7a4d]/20",
                )}
              >
                <Icon className="size-5" strokeWidth={2.5} />
              </span>
              <p className={cn("mt-2 text-[13px] font-semibold sm:text-sm", done ? "text-[#2b1d14]" : "text-[#9a8b7e]")}>{stage.label}</p>
              <p className="tnum mt-0.5 text-[11px] text-[#6b5a4e] sm:text-[13px]">
                {time && done ? formatDateTime(time) : isNow ? "đang ở đây" : " "}
              </p>
            </li>
          );
        })}
      </ol>
    </section>
  );
}

const STAGE_ICONS = [ShoppingCart, Check, Truck, Check];

function CancelPanel({ order }: { order: Order }) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const qc = useQueryClient();
  const toast = useToast();

  const cancel = useMutation({
    mutationFn: () => orderApi.cancelMine(order.id, reason),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["orders"] });
      toast.success("Đã huỷ đơn hàng");
      setOpen(false);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không huỷ được đơn."),
  });

  // Khách chỉ huỷ được đơn chưa xác nhận. Luật thật ở backend; đây chỉ quyết định hiện nút.
  if (order.status !== "PENDING") {
    return (
      <div>
        <p className="text-stone">
          Đơn đã qua bước xác nhận nên không tự huỷ được nữa. Gọi {CLINIC.phone} nếu bạn cần
          phòng khám hỗ trợ.
        </p>
      </div>
    );
  }

  return (
    <div>
      <h2 className="t-h3">Đổi ý?</h2>
      <p className="mt-3 text-stone">
        Đơn chưa được xác nhận nên bạn vẫn huỷ được. Sau khi phòng khám gom hàng thì không.
        {order.paymentMethod === "ONLINE" && order.paymentStatus === "PAID" && (
          <> Đơn đã thanh toán nên nếu huỷ, phòng khám sẽ hoàn tiền lại cho bạn.</>
        )}
      </p>
      <SiteButton variant="outline" className="mt-6 w-full" onClick={() => setOpen(true)}>
        Huỷ đơn này
      </SiteButton>

      <SiteDialog
        open={open}
        onClose={() => setOpen(false)}
        title={`Huỷ đơn ${order.orderCode}?`}
        description="Nói giúp phòng khám lý do để lần sau làm tốt hơn."
        footer={
          <>
            <SiteButton variant="outline" onClick={() => setOpen(false)}>
              Giữ đơn
            </SiteButton>
            <SiteButton
              loading={cancel.isPending}
              disabled={reason.trim() === ""}
              onClick={() => cancel.mutate()}
            >
              Huỷ đơn
            </SiteButton>
          </>
        }
      >
        <SiteTextarea
          label="Lý do huỷ"
          required
          rows={3}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Ví dụ: đặt nhầm số lượng, đã mua ở chỗ khác…"
        />
      </SiteDialog>
    </div>
  );
}
