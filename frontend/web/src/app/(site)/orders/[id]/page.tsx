"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, ChevronLeft } from "lucide-react";
import { ApiError, orderApi } from "@/lib/api";
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
    // Đơn online đang chờ trả: kiểm lại thưa thớt để thấy ngay khi đã thanh toán hoặc bị tự huỷ quá hạn.
    refetchInterval: (q) => (isAwaitingPayment(q.state.data) ? 15_000 : false),
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
          <ButtonLink href="/orders" variant="outline">
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

function OrderBody({ order }: { order: Order }) {
  const counter = order.channel === "COUNTER";

  return (
    <>
      <div className="bg-mint">
        <Container className="pt-8">
          <Link
            href="/orders"
            className="inline-flex items-center gap-1.5 text-[15px] text-stone hover:text-pine"
          >
            <ChevronLeft aria-hidden className="size-4" />
            Đơn hàng của bạn
          </Link>
        </Container>

        <Container className="py-10 md:py-14">
          <StatusPill look={orderLook(order)} />
          <h1 className="t-h2 mt-4">{counter ? "Hoá đơn" : "Đơn"} {order.orderCode}</h1>
          <p className="mt-3 text-stone">
            {counter ? "Lập tại quầy lúc" : "Đặt lúc"} {formatDateTime(order.createdAt)}
          </p>

          {order.status === "CANCELLED" && order.cancelReason && (
            <p className="measure mt-6 rounded-[var(--radius-card)] bg-white px-5 py-4 text-coral-deep">
              Lý do huỷ: {order.cancelReason}
            </p>
          )}

          {order.refundRequired && (
            <p className="measure mt-4 rounded-[var(--radius-card)] bg-white px-5 py-4 text-pine">
              Đơn này bạn đã thanh toán trước khi bị huỷ. Phòng khám sẽ hoàn tiền lại cho bạn; gọi{" "}
              {CLINIC.phone} nếu cần biết thêm.
            </p>
          )}
        </Container>
      </div>

      <Container className="grid gap-12 py-14 lg:grid-cols-[1fr_21rem] lg:gap-16 md:py-20">
        <div className="min-w-0 space-y-14">
          {/* Hoá đơn tại quầy được thu ngay nên không có chặng xác nhận và giao hàng để kể. */}
          {!counter && <Timeline order={order} />}


          <section>
            <h2 className="t-h3">{counter ? "Nội dung hoá đơn" : "Món trong đơn"}</h2>
            <ul className="mt-5 divide-y divide-mist border-y border-mist">
              {order.examAmount > 0 && (
                <li className="flex flex-wrap items-baseline gap-x-6 gap-y-2 py-4">
                  <p className="min-w-52 flex-1 font-medium">Tiền khám / thuốc</p>
                  <p className="tnum font-semibold">{formatPrice(order.examAmount)}</p>
                </li>
              )}
              {order.items.map((item) => (
                <li key={item.productId} className="flex flex-wrap items-baseline gap-x-6 gap-y-2 py-4">
                  <div className="min-w-52 flex-1">
                    <Link
                      href={`/products/${item.productId}`}
                      className="font-medium hover:text-teal-deep"
                    >
                      {item.productName}
                    </Link>
                    <p className="tnum text-[15px] text-stone">
                      {formatPrice(item.unitPrice)} × {item.quantity}
                    </p>
                  </div>
                  <p className="tnum font-semibold">{formatPrice(item.lineTotal)}</p>
                </li>
              ))}
            </ul>

            <dl className="mt-6 ml-auto max-w-sm space-y-2">
              {order.items.length > 0 && <Money label="Tiền hàng" value={order.subtotal} />}
              {order.examAmount > 0 && <Money label="Tiền khám / thuốc" value={order.examAmount} />}
              {!counter && <Money label="Phí giao hàng" value={order.shippingFee} />}
              <div className="flex justify-between border-t border-mist pt-3">
                <dt className="font-medium">Tổng cộng</dt>
                <dd className="tnum text-[21px] font-semibold">{formatPrice(order.total)}</dd>
              </div>
            </dl>
          </section>
        </div>

        <aside className="space-y-6 lg:sticky lg:top-24 lg:self-start">
          {isAwaitingPayment(order) && <PayNowPanel order={order} />}

          {counter ? (
            <div className="rounded-[var(--radius-card)] bg-mint p-6">
              <h2 className="t-h3">Thanh toán tại quầy</h2>
              <dl className="mt-4 space-y-3 text-[15px]">
                <div>
                  <dt className="text-stone">Khách hàng</dt>
                  <dd className="font-medium">{order.recipientName}</dd>
                </div>
                <div>
                  <dt className="text-stone">Hình thức</dt>
                  <dd className="font-medium">
                    {order.paymentMethod ? ORDER_PAYMENT_METHOD[order.paymentMethod] : "—"}
                  </dd>
                </div>
                {order.transferReference && (
                  <div>
                    <dt className="text-stone">Mã giao dịch</dt>
                    <dd className="font-medium">{order.transferReference}</dd>
                  </div>
                )}
                {order.paidAt && (
                  <div>
                    <dt className="text-stone">Đã thu lúc</dt>
                    <dd className="tnum font-medium">{formatDateTime(order.paidAt)}</dd>
                  </div>
                )}
                {order.note && (
                  <div>
                    <dt className="text-stone">Ghi chú</dt>
                    <dd>{order.note}</dd>
                  </div>
                )}
              </dl>
            </div>
          ) : (
          <div className="rounded-[var(--radius-card)] bg-mint p-6">
            <h2 className="t-h3">Giao tới</h2>
            <dl className="mt-4 space-y-3 text-[15px]">
              <div>
                <dt className="text-stone">Người nhận</dt>
                <dd className="font-medium">{order.recipientName}</dd>
              </div>
              <div>
                <dt className="text-stone">Điện thoại</dt>
                <dd className="tnum font-medium">{order.recipientPhone}</dd>
              </div>
              <div>
                <dt className="text-stone">Địa chỉ</dt>
                <dd className="font-medium">{order.shippingAddress}</dd>
              </div>
              {order.note && (
                <div>
                  <dt className="text-stone">Ghi chú của bạn</dt>
                  <dd>{order.note}</dd>
                </div>
              )}
              <div>
                <dt className="text-stone">Thanh toán</dt>
                <dd className="font-medium">
                  {order.paymentMethod ? ORDER_PAYMENT_METHOD[order.paymentMethod] : "—"}
                  {order.paymentMethod === "ONLINE" && (
                    <span className="ml-2 text-[15px] font-normal text-stone">
                      · {order.paymentStatus === "PAID" ? "đã thanh toán" : "chưa thanh toán"}
                    </span>
                  )}
                </dd>
              </div>
              {order.paymentStatus === "PAID" && order.paidAt && order.paymentMethod === "ONLINE" && (
                <div>
                  <dt className="text-stone">Đã thanh toán lúc</dt>
                  <dd className="tnum font-medium">{formatDateTime(order.paidAt)}</dd>
                </div>
              )}
            </dl>
          </div>
          )}

          {counter ? (
            <div className="rounded-[var(--radius-card)] border border-mist p-6">
              <p className="text-stone">
                Hoá đơn này đã thanh toán xong tại quầy. Gọi {CLINIC.phone} nếu bạn cần phòng khám
                hỗ trợ.
              </p>
            </div>
          ) : (
            <CancelPanel order={order} />
          )}
        </aside>
      </Container>
    </>
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
    <div className="rounded-[var(--radius-card)] border border-teal bg-white p-6">
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

function Money({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex justify-between">
      <dt className="text-stone">{label}</dt>
      <dd className="tnum">{formatPrice(value)}</dd>
    </div>
  );
}

const FLOW: Array<{ status: OrderStatus; label: string; note: string }> = [
  { status: "PENDING", label: "Bạn đã đặt", note: "Phòng khám nhận được đơn." },
  { status: "CONFIRMED", label: "Đã xác nhận", note: "Phòng khám đã gom hàng cho bạn." },
  { status: "SHIPPING", label: "Đang giao", note: "Hàng đang trên đường tới bạn." },
  { status: "COMPLETED", label: "Đã giao xong", note: "Bạn đã nhận và trả tiền." },
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
      <section>
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
    <section>
      <h2 className="t-h3">Đơn đang ở đâu</h2>

      <ol className="mt-6">
        {FLOW.map((stage, i) => {
          const done = i <= current;
          const isNow = i === current;
          const time = at.get(stage.status);
          const note = stage.status === "PENDING" ? pendingNote(order) : stage.note;

          return (
            <li key={stage.status} className="flex gap-5">
              <div className="flex flex-col items-center">
                <span
                  aria-hidden
                  className={cn(
                    "grid size-9 shrink-0 place-items-center rounded-full text-[14px] font-semibold",
                    done ? "bg-teal text-white" : "border border-mist bg-white text-stone",
                  )}
                >
                  {done ? <Check className="size-4" /> : i + 1}
                </span>
                {i < FLOW.length - 1 && (
                  <span
                    aria-hidden
                    className={cn("w-px flex-1", i < current ? "bg-teal" : "bg-mist")}
                  />
                )}
              </div>

              <div className={cn("pb-8", i === FLOW.length - 1 && "pb-0")}>
                <p className={cn("font-medium", !done && "text-stone")}>
                  {stage.label}
                  {isNow && <span className="ml-2 text-[15px] text-teal-deep">đang ở đây</span>}
                </p>
                <p className="text-[15px] text-stone">{note}</p>
                {time && done && (
                  <p className="tnum mt-1 text-[15px] text-stone">{formatDateTime(time)}</p>
                )}
              </div>
            </li>
          );
        })}
      </ol>
    </section>
  );
}

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
      <div className="rounded-[var(--radius-card)] border border-mist p-6">
        <p className="text-stone">
          Đơn đã qua bước xác nhận nên không tự huỷ được nữa. Gọi {CLINIC.phone} nếu bạn cần
          phòng khám hỗ trợ.
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-[var(--radius-card)] border border-mist p-6">
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
